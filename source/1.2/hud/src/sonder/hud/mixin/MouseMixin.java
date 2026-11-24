package sonder.hud.mixin;
import sonder.hud.Hud;
import sonder.hud.Menu;
import net.minecraft.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(class_312.class)
class MouseMixin {
    @Inject(method="method_1601",at=@At("HEAD"))
    private void sonderClick(long window,class_11910 mouse,int action,CallbackInfo ci){
        var mc=Hud.game();if(action==1&&window==mc.method_22683().method_4490()&&mc.method_1569()&&mc.field_1724!=null&&mc.field_1755==null)Hud.click(mouse.comp_4801());
    }
}
