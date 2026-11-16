package dev.vestige.client.rpg;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.vestige.client.combat.ClientActions;
import dev.vestige.client.combat.ClientCombat;
import dev.vestige.combat.machine.RunningAction;
import dev.vestige.player.PlayerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws the Wick Flask in the left hand while the player drinks (or shakes an empty flask). */
public class FlaskLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final ItemInHandRenderer items;

    public FlaskLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, ItemInHandRenderer items) {
        super(parent);
        this.items = items;
    }

    public static boolean drinking(AbstractClientPlayer player) {
        RunningAction a = player == Minecraft.getInstance().player ? ClientCombat.machine().current() : ClientActions.of(player);
        return a != null && (a.ref.action().equals("flask_drink") || a.ref.action().equals("flask_empty"));
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!drinking(player)) {
            return;
        }
        ps.pushPose();
        getParentModel().translateToHand(HumanoidArm.LEFT, ps);
        ps.mulPose(Axis.XP.rotationDegrees(-90f));
        ps.mulPose(Axis.YP.rotationDegrees(180f));
        ps.translate(-1f / 16f, 0.125f, -0.625f);
        items.renderItem(player, new ItemStack(PlayerModule.WICK_FLASK.get()), ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true, ps, buffers, light);
        ps.popPose();
    }
}
