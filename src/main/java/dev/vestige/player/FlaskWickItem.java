package dev.vestige.player;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A Flask Wick (GDD §8.4): use to add a charge to the Wick Flask (max 10). */
public class FlaskWickItem extends Item {
    public FlaskWickItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        ServerPlayer sp = (ServerPlayer) player;
        if (!FlaskService.addWick(sp)) {
            sp.displayClientMessage(Component.translatable("message.vestige.flask_full"), true);
            return InteractionResultHolder.fail(stack);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6f, 1.4f);
        sp.displayClientMessage(Component.translatable("message.vestige.flask_wick", FlaskService.get(sp).max()), true);
        stack.consume(1, player);
        return InteractionResultHolder.consume(stack);
    }
}
