// Sonder's Windows entry point provisions the Java runtime for the standalone launcher.
package main

import (
	"archive/zip"
	"crypto/sha256"
	_ "embed"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"
)

//go:embed Sonder.jar
var app []byte

type component struct{ name, version, url, hash, executable string }

var java = component{"Java", "zulu-21.0.13.1", "https://cdn.azul.com/zulu/bin/zulu21.54.201-ca-jre21.0.13.1-win_x64.zip", "888995f738298e22c34c3e4bf72230573360e4b63a19455ec76c0bf59235a972", "javaw.exe"}

func unpack(archive, dest string) error {
	reader, err := zip.OpenReader(archive)
	if err != nil {
		return err
	}
	defer reader.Close()
	for _, f := range reader.File {
		name := filepath.FromSlash(strings.TrimSuffix(f.Name, "/"))
		if !filepath.IsLocal(name) || f.Mode()&os.ModeSymlink != 0 {
			return fmt.Errorf("invalid archive entry %s", f.Name)
		}
		path := filepath.Join(dest, name)
		if f.FileInfo().IsDir() {
			if err := os.MkdirAll(path, 0755); err != nil {
				return err
			}
			continue
		}
		if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
			return err
		}
		if err := extractFile(f, path); err != nil {
			return err
		}
	}
	return nil
}
func extractFile(f *zip.File, path string) error {
	in, err := f.Open()
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_EXCL, 0644)
	if err != nil {
		return err
	}
	_, err = io.Copy(out, in)
	closed := out.Close()
	if err != nil {
		return err
	}
	return closed
}
func provision(root string, c component) (string, error) {
	dst := filepath.Join(root, c.version)
	find := func(dir string) (string, error) {
		raw, err := os.ReadFile(filepath.Join(dir, "sonder-entry.txt"))
		if err != nil {
			return "", err
		}
		rel := string(raw)
		if !filepath.IsLocal(rel) || !strings.EqualFold(filepath.Base(rel), c.executable) {
			return "", errors.New("invalid runtime entry")
		}
		exe := filepath.Join(dir, rel)
		_, err = os.Stat(exe)
		return exe, err
	}
	if path, err := find(dst); err == nil {
		return path, nil
	}
	fmt.Println("Downloading", c.name, "from its publisher...")
	tmp, err := os.MkdirTemp(root, "setup-")
	if err != nil {
		return "", err
	}
	defer os.RemoveAll(tmp)
	archive := filepath.Join(tmp, "package.zip")
	client := http.Client{Timeout: 10 * time.Minute}
	response, err := client.Get(c.url)
	if err != nil {
		return "", err
	}
	defer response.Body.Close()
	if response.StatusCode != 200 {
		return "", fmt.Errorf("%s returned %s", c.name, response.Status)
	}
	file, err := os.Create(archive)
	if err != nil {
		return "", err
	}
	hash := sha256.New()
	_, err = io.Copy(io.MultiWriter(file, hash), response.Body)
	closeErr := file.Close()
	if err != nil {
		return "", err
	}
	if closeErr != nil {
		return "", closeErr
	}
	if hex.EncodeToString(hash.Sum(nil)) != c.hash {
		return "", fmt.Errorf("%s checksum mismatch", c.name)
	}
	fmt.Println("Installing", c.name, "for Sonder...")
	stage := filepath.Join(tmp, "unpacked")
	if err = unpack(archive, stage); err != nil {
		return "", err
	}
	var entry string
	err = filepath.WalkDir(stage, func(path string, d os.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if !d.IsDir() && strings.EqualFold(d.Name(), c.executable) {
			entry, err = filepath.Rel(stage, path)
			return err
		}
		return nil
	})
	if err != nil {
		return "", err
	}
	if entry == "" {
		return "", fmt.Errorf("%s executable missing", c.name)
	}
	if err = os.WriteFile(filepath.Join(stage, "sonder-entry.txt"), []byte(entry), 0644); err != nil {
		return "", err
	}
	if err = os.Rename(stage, dst); err != nil {
		if path, ok := find(dst); ok == nil {
			return path, nil
		}
		return "", err
	}
	return find(dst)
}
func run() error {
	home, err := os.UserHomeDir()
	if err != nil {
		return err
	}
	root := filepath.Join(home, ".sonder-client", "runtime")
	if err = os.MkdirAll(root, 0755); err != nil {
		return err
	}
	j, err := provision(root, java)
	if err != nil {
		return err
	}
	sum := sha256.Sum256(app)
	jar := filepath.Join(root, fmt.Sprintf("sonder-%x.jar", sum[:8]))
	existing, err := os.ReadFile(jar)
	if err != nil || sha256.Sum256(existing) != sum {
		file, err := os.CreateTemp(root, "launcher-*.tmp")
		if err != nil {
			return err
		}
		defer os.Remove(file.Name())
		_, err = file.Write(app)
		closed := file.Close()
		if err != nil {
			return err
		}
		if closed != nil {
			return closed
		}
		if err = os.Rename(file.Name(), jar); err != nil {
			return err
		}
	}
	cmd := exec.Command(j, "-jar", jar)
	self, err := os.Executable()
	if err != nil {
		return err
	}
	cmd.Env = append(os.Environ(), "SONDER_PACKS="+filepath.Join(filepath.Dir(self), "packs"), "SONDER_EXECUTABLE="+self, "JAVA_HOME="+filepath.Dir(filepath.Dir(j)))
	return cmd.Start()
}
func main() {
 if len(os.Args)==2 && os.Args[1]=="--game-java25" {
  home,err:=os.UserHomeDir()
  if err!=nil {os.Exit(1)}
  root:=filepath.Join(home,".sonder-client","runtime")
  runtime:=component{"Java 25","zulu-25.0.5.1","https://cdn.azul.com/zulu/bin/zulu25.38.201-ca-jre25.0.5.1-win_x64.zip","6c2c84c1541ea2db58bf15387454b21911505ede8d02cfcb7afba2de680ae379","javaw.exe"}
  path,err:=provision(root,runtime)
  if err!=nil {fmt.Fprintln(os.Stderr,err);os.Exit(1)}
  fmt.Println(path)
  return
 }
	if len(os.Args) == 2 && (os.Args[1] == "--protect" || os.Args[1] == "--unprotect") {
		if !vault(os.Args[1]) {
			os.Exit(1)
		}
		return
	}
	fmt.Println("Sonder Client • Windows x64")
	if err := run(); err != nil {
		fmt.Println("Sonder could not start:", err)
		fmt.Println("Press Enter to close.")
		fmt.Scanln()
		os.Exit(1)
	}
}
