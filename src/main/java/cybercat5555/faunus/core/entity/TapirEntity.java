package cybercat5555.faunus.core.entity;

import cybercat5555.faunus.core.EffectStatusRegistry;
import cybercat5555.faunus.core.EntityRegistry;
import cybercat5555.faunus.core.ItemRegistry;
import cybercat5555.faunus.core.entity.ai.goals.RamGoal;
import cybercat5555.faunus.util.FaunusID;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.task.BreedTask;
import net.minecraft.entity.ai.goal.AnimalMateGoal;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.tslat.smartbrainlib.api.SmartBrainOwner;
import net.tslat.smartbrainlib.api.core.BrainActivityGroup;
import net.tslat.smartbrainlib.api.core.SmartBrainProvider;
import net.tslat.smartbrainlib.api.core.behaviour.FirstApplicableBehaviour;
import net.tslat.smartbrainlib.api.core.behaviour.OneRandomBehaviour;
import net.tslat.smartbrainlib.api.core.behaviour.custom.misc.Idle;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.FleeTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.FollowEntity;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.MoveToWalkTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.path.SetRandomWalkTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.target.InvalidateAttackTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.target.SetPlayerLookTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.target.SetRandomLookTarget;
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.HurtBySensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.ItemTemptingSensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.NearbyLivingEntitySensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.NearbyPlayersSensor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

public class TapirEntity extends AnimalEntity implements GeoEntity, SmartBrainOwner<TapirEntity> {
    public static final TagKey<Item> TAPIR_BREED_ITEMS = TagKey.of(RegistryKeys.ITEM, FaunusID.content("tapir_breeding_items"));

    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    protected static final RawAnimation WALKING_ANIM = RawAnimation.begin().thenLoop("walking");
    protected static final RawAnimation RUNNING_ANIM = RawAnimation.begin().thenLoop("running");
    protected static final RawAnimation EAR_TWITCH_ANIM = RawAnimation.begin().thenPlayXTimes("ear_twitch_both", 3);
    protected static final RawAnimation EAR_TWITCH_ANIM_LEFT = RawAnimation.begin().thenPlayXTimes("ear_twitch_left", 2);
    protected static final RawAnimation EAR_TWITCH_ANIM_RIGHT = RawAnimation.begin().thenPlayXTimes("ear_twitch_right", 2);
    protected static final RawAnimation EAR_TWITCH_SNIFF_ANIM_RIGHT = RawAnimation.begin().thenPlayXTimes("sniffing_ear_right", 2);
    protected static final RawAnimation EAR_TWITCH_SNIFF_ANIM_LEFT = RawAnimation.begin().thenPlayXTimes("sniffing_ear_left", 2);
    protected static final RawAnimation SNIFFING_ANIM = RawAnimation.begin().thenPlay("sniffing").thenLoop("idle");
    protected static final RawAnimation LAYING_DOWN_ANIM = RawAnimation.begin().thenPlayAndHold("laying_down").thenWait(999).thenLoop("idle");

    /**
     * The chance that the tapir will get the stinky effect when damaged. Being 0.1f means 10% chance.
     */
    private static final float STINKY_EFFECT_CHANCE = 0.1f;

    /**
     * The time it takes for the tapir to recharge the stinky effect. Being 20 * 60 means 60 seconds, since a second is 20 ticks.
     */
    private static final int STINKY_RECHARGE_TIME = 20 * 60;

    /**
     * Remaining time for be able to the tapir to recharge the stinky effect.
     */
    private int stinkyRecharge = STINKY_RECHARGE_TIME;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    protected int tempInt = 0;
    protected EarTwitchMode earMode = EarTwitchMode.NONE;

    public TapirEntity(EntityType<? extends TapirEntity> entityType, World world) {
        super(entityType, world);
    }

    public static DefaultAttributeContainer.Builder createMobAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 10)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4);
    }

    @Override
    public final void initGoals() {
        goalSelector.add(1, new EscapeDangerGoal(this, 2.5d));
        goalSelector.add(2, new LookAroundGoal(this));
        goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 3.0f));

        targetSelector.add(1, new AnimalMateGoal(this, 1.0));
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack handItem = player.getStackInHand(hand);
        feedEntity(player, handItem);
        milkTapir(player, hand, handItem);

        return super.interactMob(player, hand);
    }

    /**
     * Breeds the tapir with the player if the player is holding the breeding item and the tapir is not a baby.
     * @param player The player that is interacting with the tapir.
     * @param handItem The item that the player is holding. Must be a tapir breeding item.
     */
    private void feedEntity(PlayerEntity player, ItemStack handItem) {
        if (isBreedingItem(handItem) && !this.isBaby()) {
            this.lovePlayer(player);
        }
    }

    /**
     * Milks the tapir if the player is holding a glass bottle and the tapir can be milked. The tapir will give a bottled stinky item.
     * @param player The player that is interacting with the tapir.
     * @param hand The hand that the player is using to interact with the tapir, so the hand is holding the glass bottle.
     * @param handItem The item that the player is holding. Must be a glass bottle.
     */
    private void milkTapir(PlayerEntity player, Hand hand, ItemStack handItem) {
        if (handItem.isOf(Items.GLASS_BOTTLE) && canGetMilked() && !this.isBaby()) {
            player.playSound(SoundEvents.ENTITY_COW_MILK, 1.0F, 1.0F);
            ItemStack bottleOfStinky = ItemRegistry.BOTTLED_STINK.getDefaultStack();
            player.setStackInHand(hand, bottleOfStinky);
            stinkyRecharge = 0;
        }
    }

    @Override
    public void onDamaged(DamageSource damageSource) {
        if (!this.isBaby() && random.nextFloat() < STINKY_EFFECT_CHANCE && damageSource.getSource() instanceof LivingEntity attacker) {
            StatusEffectInstance stinkyEffect = new StatusEffectInstance(EffectStatusRegistry.STINKY_EFFECT, 20 * 60);
            attacker.addStatusEffect(stinkyEffect);
        }

        super.onDamaged(damageSource);
    }

    @Override
    public void mobTick() {
        super.mobTick();
        tickBrain(this);
        stinkyRecharge = Math.min(stinkyRecharge + 1, STINKY_RECHARGE_TIME);

        if (getWorld().isClient) {
            tempInt = random.nextInt(1024);
        }
    }


    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return stack.isIn(TAPIR_BREED_ITEMS);
    }

    private boolean canGetMilked() {
        return stinkyRecharge >= STINKY_RECHARGE_TIME;
    }

    @Override
    public TapirEntity createChild(ServerWorld world, PassiveEntity entity) {
        return EntityRegistry.TAPIR.create(world);
    }

    @Override
    protected Brain.Profile<?> createBrainProfile() {
        return new SmartBrainProvider<>(this);
    }

    @Override
    public List<? extends ExtendedSensor<? extends TapirEntity>> getSensors() {
        return ObjectArrayList.of
                (new NearbyLivingEntitySensor<>(),
                        new NearbyPlayersSensor<>(),
                        new HurtBySensor<>(),
                        new ItemTemptingSensor<>());
    }

    @Override
    public BrainActivityGroup<TapirEntity> getCoreTasks() {
        return BrainActivityGroup.coreTasks
                (new FollowEntity<>(),
                        new MoveToWalkTarget<>(),
                        new BreedTask(EntityRegistry.TAPIR, 1f));
    }

    @SuppressWarnings("unchecked")
    @Override
    public BrainActivityGroup<TapirEntity> getIdleTasks() {
        return BrainActivityGroup.idleTasks
                (new FirstApplicableBehaviour<TapirEntity>
                                (new SetPlayerLookTarget<>(), new SetRandomLookTarget<>()),
                        new OneRandomBehaviour<TapirEntity>
                                (new SetRandomWalkTarget<>(), new Idle<>().runFor(entity -> entity.getRandom().nextBetween(60, 600))));
    }

    @SuppressWarnings("unchecked")
    @Override
    public BrainActivityGroup<TapirEntity> getFightTasks() {
        return BrainActivityGroup.fightTasks
                (new InvalidateAttackTarget<>(), new FirstApplicableBehaviour<TapirEntity>(new FleeTarget<>()));
    }

    @Override
    public void registerControllers(ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 5, this::idleAnimController));
        controllers.add(new AnimationController<>(this, "ears", 5, this::earAnimController));
    }

    protected <E extends TapirEntity> PlayState idleAnimController(final AnimationState<E> state) {
        if (isSubmergedInWater()) {
            state.setAndContinue(WALKING_ANIM);
            return PlayState.CONTINUE;
        }

        if (state.isMoving()) {
            state.setAndContinue(isSprinting() ? RUNNING_ANIM : WALKING_ANIM);
        } else if (state.isCurrentAnimation(WALKING_ANIM)) {
            state.setAndContinue(IDLE_ANIM);
        }

        if (state.isCurrentAnimation(IDLE_ANIM) && tempInt < 5) {
            state.setAndContinue(LAYING_DOWN_ANIM);
        }

        return PlayState.CONTINUE;
    }

    protected <E extends TapirEntity> PlayState earAnimController(final AnimationState<E> state) {
        if (state.getController().hasAnimationFinished()) {
            earMode = EarTwitchMode.NONE;
        }

        if (earMode == EarTwitchMode.NONE) {
            int random = tempInt;

            if (random < 8) {
                state.setAndContinue(EAR_TWITCH_ANIM);
                earMode = EarTwitchMode.BOTH;
                return PlayState.CONTINUE;
            } else if (random < 24) {
                boolean left = random % 2 == 0;
                state.setAndContinue(left ? EAR_TWITCH_ANIM_LEFT : EAR_TWITCH_ANIM_RIGHT);
                earMode = left ? EarTwitchMode.LEFT : EarTwitchMode.RIGHT;
                return PlayState.CONTINUE;
            }
        }

        return earMode == EarTwitchMode.NONE ? PlayState.STOP : PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    protected enum EarTwitchMode {
        NONE,
        LEFT,
        RIGHT,
        BOTH;
    }
}