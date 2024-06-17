package cybercat5555.faunus.core.entity.livingEntity;

import cybercat5555.faunus.core.entity.ai.goals.BuryIntoFloor;
import cybercat5555.faunus.core.entity.ai.goals.TerritorialSelectorGoal;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.AquaticMoveControl;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class SnappingTurtleEntity extends PathAwareEntity implements GeoEntity {
    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    protected static final RawAnimation BURIED_ANIM = RawAnimation.begin().thenLoop("buried");
    protected static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    protected static final RawAnimation BITE_ANIM = RawAnimation.begin().thenLoop("bite");
    protected static final RawAnimation SWIM_ANIM = RawAnimation.begin().thenLoop("swimming");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public SnappingTurtleEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        this.moveControl = new AquaticMoveControl(this, 85, 10, 1F, 0.5F, true);
    }


    public static DefaultAttributeContainer.Builder createMobAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 35f)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25f)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4f)
                .add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 0.1f)
                .add(EntityAttributes.GENERIC_ATTACK_SPEED, 1f);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SnappingTurtleAttack(this, 0.5D, false));
        goalSelector.add(1, new BuryIntoFloor(this, Blocks.MUD));
        goalSelector.add(2, new SwimGoal(this));
        goalSelector.add(2, new WanderAroundGoal(this, 0.25D, 120, true));
        goalSelector.add(3, new MoveIntoWaterGoal(this));

        targetSelector.add(0, new RevengeGoal(this));
        targetSelector.add(1, new TerritorialSelectorGoal<>(this, LivingEntity.class, true, false,
                target -> (this.squaredDistanceTo(target) < 4.0D)));
    }

    @Override
    public void registerControllers(ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 5, this::idleAnimController));
    }

    protected <E extends SnappingTurtleEntity> PlayState idleAnimController(final AnimationState<E> event) {
        boolean isBuried = getWorld().getBlockState(getBlockPos()).isOf(Blocks.MUD);

        if (event.isMoving()) {
            event.setAnimation(isSubmergedInWater() ? SWIM_ANIM : WALK_ANIM);
        } else if(isAttacking()) {
            event.setAnimation(BITE_ANIM);
        } else {
            event.setAnimation(isBuried ? BURIED_ANIM : IDLE_ANIM);
        }

        return PlayState.CONTINUE;
    }

    static class SnappingTurtleAttack extends MeleeAttackGoal {
        private static final int ATTACK_COOLDOWN = 20;
        private int attackCooldown;

        public SnappingTurtleAttack(PathAwareEntity mob, double speed, boolean pauseWhenMobIdle) {
            super(mob, speed, pauseWhenMobIdle);
        }

        @Override
        protected void attack(LivingEntity target, double squaredDistance) {
            if (this.mob instanceof SnappingTurtleEntity &&
                    this.attackCooldown-- <= 0 && squaredDistance < 2.0D) {
                this.attackCooldown = ATTACK_COOLDOWN;
                this.mob.tryAttack(target);
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2));
            }
        }
    }
}
