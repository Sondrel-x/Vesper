package sonder.hud.mixin;
import sonder.hud.Hud;
import sonder.hud.Menu;
import net.minecraft.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(class_309.class)
class KeyboardMixin {
    @Inject(method="method_1466",at=@At("HEAD"),cancellable=true)
    private void sonderMenu(long window,int action,class_11908 key,CallbackInfo ci){
        var mc=Hud.game();if(action!=1||key.comp_4795()!=344||window!=mc.method_22683().method_4490()||mc.field_1724==null)return;
        if(mc.field_1755==null){mc.method_1507(new Menu());ci.cancel();}
        else if(mc.field_1755 instanceof Menu){mc.method_1507(null);ci.cancel();}
    }
}
