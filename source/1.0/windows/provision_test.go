package main
import ("testing";"os";"path/filepath")
func TestPublishers(t *testing.T){
 root:=t.TempDir()
 for _,c:=range []component{java}{
  p,err:=provision(root,c);if err!=nil{t.Fatal(err)};f,_:=os.Open(p);var hdr [2]byte;f.Read(hdr[:]);f.Close();if string(hdr[:])!="MZ"{t.Fatal("invalid Windows executable")};again,err:=provision(root,c);if err!=nil||again!=p{t.Fatal("cached lookup failed")};t.Log(filepath.Base(p))
 }
}
