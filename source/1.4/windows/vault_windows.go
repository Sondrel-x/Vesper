package main

import (
	"io"
	"os"
	"runtime"
	"syscall"
	"unsafe"
)

type blob struct {
	length uint32
	data   *byte
}

func vault(mode string) bool {
	data, err := io.ReadAll(io.LimitReader(os.Stdin, 65536))
	if err != nil || len(data) == 0 {
		return false
	}
	input := blob{uint32(len(data)), &data[0]}
	var output blob
	function := "CryptProtectData"
	if mode == "--unprotect" {
		function = "CryptUnprotectData"
	}
	lib := syscall.NewLazyDLL("crypt32.dll")
	ok, _, _ := lib.NewProc(function).Call(uintptr(unsafe.Pointer(&input)), 0, 0, 0, 0, 1, uintptr(unsafe.Pointer(&output)))
	runtime.KeepAlive(data)
	if ok == 0 {
		return false
	}
	defer syscall.NewLazyDLL("kernel32.dll").NewProc("LocalFree").Call(uintptr(unsafe.Pointer(output.data)))
	_, err = os.Stdout.Write(unsafe.Slice(output.data, output.length))
	return err == nil
}
