package com.mechaniciancombat.entity;

import com.mechaniciancombat.TkGunServer;
import com.mechaniciancombat.mixin.TridentEntityAccessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 忠诚回归用的猎矛投射物：用猎矛物品模型渲染，靠忠诚数据飞回主人。
 *
 * <p>忠诚值直接写进三叉戟的同步数据，但**不**真的加附魔，
 * 所以物品提示与附魔光效不会出现。
 */
public final class HunterSpearReturnEntity extends ThrownTrident {
    public HunterSpearReturnEntity(EntityType<? extends HunterSpearReturnEntity> type, Level level) {
        super(type, level);
    }

    public HunterSpearReturnEntity(EntityType<? extends HunterSpearReturnEntity> type, Level level,
                                   LivingEntity owner, ItemStack stack) {
        super(type, level);
        setOwner(owner);
        ((TridentEntityAccessor) (Object) this).tkgun$setTridentItem(stack.copyWithCount(1));
        entityData.set(TridentEntityAccessor.tkgun$getLoyaltyData(), (byte) 3);
        entityData.set(TridentEntityAccessor.tkgun$getEnchantedData(), false);
        ((TridentEntityAccessor) (Object) this).tkgun$setDealtDamage(true);
    }

    @Override
    protected ItemStack getPickupItem() {
        return TkGunServer.HUNTER_SPEAR == null
                ? ItemStack.EMPTY : new ItemStack(TkGunServer.HUNTER_SPEAR);
    }
}
