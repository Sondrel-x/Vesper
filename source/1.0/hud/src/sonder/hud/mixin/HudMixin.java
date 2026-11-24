package sonder.hud.mixin;
import sonder.hud.Hud;
import sonder.hud.Menu;
import net.minecraft.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(class_329.class)
class HudMixin {
    @Inject(method="method_1753",at=@At("TAIL"))
    private void sonderHud(class_332 context,class_9779 tick,CallbackInfo ci){
        var mc=Hud.game();if(mc.field_1724!=null&&!mc.field_1690.field_1842&&!(mc.field_1755 instanceof Menu))Hud.draw(context,false);
    }
    @Inject(method="method_55803",at=@At("HEAD"),cancellable=true)
    private void sonderBoard(class_332 context,class_9779 tick,CallbackInfo ci){if(Hud.replaceBoard())ci.cancel();}
    @Inject(method="method_1765",at=@At("HEAD"),cancellable=true)
    private void sonderEffects(class_332 context,class_9779 tick,CallbackInfo ci){if(Hud.cfg.flag("EFFECTS"))ci.cancel();}
}
