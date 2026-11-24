import java.io.IOException;
import java.nio.file.*;
import java.util.*;

final class AccountShelf {
    private final Path root, slots, selection;
    AccountShelf(Path root)throws IOException{
        this.root=root;
        slots=Files.createDirectories(root.resolve("accounts"));
        selection=root.resolve("active-account.properties");
    }
    Accounts current()throws IOException{
        String key=Engine.read(selection).getProperty("slot","");
        if(key.matches("[a-f0-9-]{36}") && Files.isDirectory(slots.resolve(key)))
            return new Accounts(root,slots.resolve(key));
        return new Accounts(root);
    }
    List<Accounts> saved()throws IOException{
        var result=new ArrayList<Accounts>();
        var original=new Accounts(root);
        if(Files.isRegularFile(original.vault()))result.add(original);
        try(var dirs=Files.list(slots)){
            for(var dir:dirs.sorted().toList()){
                var account=new Accounts(root,dir);
                if(Files.isRegularFile(account.vault()))result.add(account);
            }
        }
        return result;
    }
    Accounts create()throws IOException{
        return new Accounts(root,Files.createDirectory(slots.resolve(UUID.randomUUID().toString())));
    }
    void activate(Accounts account)throws IOException{
        var p=new Properties();
        if(!account.base.equals(root))p.setProperty("slot",account.base.getFileName().toString());
        Engine.save(selection,p);
    }
    void discard(Accounts candidate)throws IOException{
        if(!slots.equals(candidate.base.getParent()))return;
        try(var files=Files.list(candidate.base)){for(Path file:files.toList())Files.deleteIfExists(file);}
        Files.deleteIfExists(candidate.base);
    }
}
