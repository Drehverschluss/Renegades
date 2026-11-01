package com.drehverschluss.renegades.registry;

import com.drehverschluss.renegades.entity.RenegadeClasses;
import java.util.function.Supplier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

/**
 * Spawn egg of a renegade class: does nothing but tell the player which mods are missing if the class is not available
 * (see RenegadeClasses), e.g. an egg from /give without the class mod.
 */
public class RenegadeSpawnEggItem extends DeferredSpawnEggItem {
    public RenegadeSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor, int highlightColor, Item.Properties props) {
        super(type, backgroundColor, highlightColor, props);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // CONSUME (not FAIL) ends the interaction, so use() does not run as well and report a second time.
        if (!this.available(context.getItemInHand(), context.getPlayer())) {
            return InteractionResult.CONSUME;
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!this.available(stack, player)) {
            return InteractionResultHolder.consume(stack);
        }
        return super.use(level, player, hand);
    }

    private boolean available(ItemStack stack, Player player) {
        EntityType<?> type = this.getType(stack);
        if (RenegadeClasses.isAvailable(type)) {
            return true;
        }
        if (player != null && !player.level().isClientSide()) {
            player.displayClientMessage(RenegadeClasses.unavailableMessage(type), false);
        }
        return false;
    }
}
