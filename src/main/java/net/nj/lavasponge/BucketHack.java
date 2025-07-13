package net.nj.lavasponge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.*;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class BucketHack {
    private final MinecraftClient client;
    private PlayerEntity player;
    private Vec3d playerVec;
    private Vec3d destroyerVec;
    private Vec3d pickupVec;
    private List<BlockPos> blockList;
    private boolean placeBlockStateHackFix;
    private boolean lavaDestroyed;
    private HashMap<BlockPos, BlockState> blockStateMap = new HashMap<>();

    public BucketHack(MinecraftClient client) {
        this.client = client;
        this.player = client.player;
    }

    public void useBucketer() {
        if (client.player == null || client.world == null) return;

        this.player = client.player;
        this.lavaDestroyed = false;

        playerVec = player.getPos().add(0, player.getEyeHeight(player.getPose()), 0);
        blockList = genBlockList();

        List<BlockPos> lavaList = getLavaList();
        if (lavaList.isEmpty()) {
            return;
        }

        getDestroyerBlock();
        if (destroyerVec == null || pickupVec == null) {
            return;
        }

        Item item = player.getMainHandStack().getItem();
        if (item == Items.LAVA_BUCKET || item == Items.BUCKET) {
            placeBlockStateHackFix = true;

            if (item == Items.LAVA_BUCKET) {
                placeOnDestroyerBlock(destroyerVec);
            }

            destroyLava(lavaList);

            placeOnDestroyerBlock(pickupVec);

            if (lavaDestroyed) {
                player.playSound(SoundEvents.ITEM_BUCKET_FILL, 1.0F, 1.0F);
            }
        }
    }

    private List<BlockPos> getLavaList() {
        List<BlockPos> lavaList = new ArrayList<>();
        for (BlockPos pos : blockList) {
            BlockState blockState = client.world.getBlockState(pos);
            setBlockState(pos, blockState);

            if (blockState.isOf(Blocks.LAVA)) {
                if (blockState.getFluidState().isStill()) {
                    lavaList.add(pos);
                }
            }
        }
        return lavaList;
    }

    private List<BlockPos> genBlockList() {
        BlockPos playerPos = player.getBlockPos();
        List<BlockPos> list = new ArrayList<>();

        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    list.add(playerPos.add(x, y, z));
                }
            }
        }

        Collections.sort(list, (b1, b2) -> {
            Vec3d b1vec = Vec3d.ofCenter(b1);
            Vec3d b2vec = Vec3d.ofCenter(b2);
            double b1dist = b1vec.distanceTo(playerVec);
            double b2dist = b2vec.distanceTo(playerVec);
            return Double.compare(b1dist, b2dist);
        });

        return list;
    }

    private void getDestroyerBlock() {
        for (int i = blockList.size() - 1; i >= 0; i--) {
            BlockPos pos = blockList.get(i);
            BlockState blockState = client.world.getBlockState(pos);

            if (!blockState.isAir() && blockState.isSolidBlock(client.world, pos)) {
                if (tryDestroyerBlock(pos, blockState)) {
                    return;
                }
            }
        }

        BlockPos playerPos = player.getBlockPos();

        for (int radius = 2; radius <= 8; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    for (int y = -2; y <= 2; y++) {
                        BlockPos checkPos = playerPos.add(x, y, z);
                        BlockState checkState = client.world.getBlockState(checkPos);

                        if (!checkState.isAir() && checkState.isSolidBlock(client.world, checkPos)) {
                            if (trySimpleDestroyerBlock(checkPos)) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean tryDestroyerBlock(BlockPos pos, BlockState blockState) {
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        Vec3d[] testPoints = {
                blockCenter.add(0.5, 0, 0),
                blockCenter.add(-0.5, 0, 0),
                blockCenter.add(0, 0, 0.5),
                blockCenter.add(0, 0, -0.5),
                blockCenter.add(0, 0.5, 0),
                blockCenter.add(0, -0.5, 0),
                blockCenter
        };

        for (Vec3d testPoint : testPoints) {
            float yaw = getYaw(testPoint);
            float pitch = getPitch(testPoint);

            BlockHitResult ray = rayTrace(client.world, pitch, yaw, false);

            if (ray != null && ray.getType() == HitResult.Type.BLOCK) {
                BlockPos hitPos = ray.getBlockPos();

                if (hitPos.equals(pos) || hitPos.getManhattanDistance(pos) <= 1) {
                    BlockPos targetPos = ray.getBlockPos().offset(ray.getSide());
                    BlockState targetState = client.world.getBlockState(targetPos);

                    if (isValidTargetPosition(targetPos, targetState)) {
                        destroyerVec = testPoint;
                        pickupVec = Vec3d.ofCenter(targetPos);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean trySimpleDestroyerBlock(BlockPos pos) {
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        float yaw = getYaw(blockCenter);
        float pitch = getPitch(blockCenter);

        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};

        for (Direction dir : directions) {
            BlockPos targetPos = pos.offset(dir);
            BlockState targetState = client.world.getBlockState(targetPos);

            if (isValidTargetPosition(targetPos, targetState)) {
                destroyerVec = blockCenter;
                pickupVec = Vec3d.ofCenter(targetPos);
                return true;
            }
        }

        return false;
    }

    private boolean isValidTargetPosition(BlockPos targetPos, BlockState targetState) {
        boolean isAirOrReplaceable = targetState.isAir() ||
                targetState.isOf(Blocks.WATER) ||
                targetState.isOf(Blocks.LAVA) ||
                targetState.getBlock().getDefaultState().canPlaceAt(client.world, targetPos);

        boolean notPlayerPosition = !targetPos.equals(player.getBlockPos()) &&
                !targetPos.equals(player.getBlockPos().down()) &&
                !targetPos.equals(player.getBlockPos().up());

        boolean safeDistance = targetPos.getSquaredDistance(player.getBlockPos()) >= 1;

        return isAirOrReplaceable && notPlayerPosition && safeDistance;
    }

    private void destroyLava(List<BlockPos> lavaList) {
        for (BlockPos pos : lavaList) {
            lavaDestroyed = true;
            Vec3d v = getBlockSpot(pos, playerVec, new Box(pos), true);
            if (v == null) continue;

            pickupLava(pos, v);
            placeOnDestroyerBlock(destroyerVec);
        }
    }

    private void pickupLava(BlockPos pos, Vec3d v) {
        if (v == null) return;

        float yaw = getYaw(v);
        float pitch = getPitch(v);

        client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround(), false));

        client.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(
                Hand.MAIN_HAND,
                0,
                yaw,
                pitch
        ));

        setBlockState(pos, Blocks.AIR.getDefaultState());
    }

    private void placeOnDestroyerBlock(Vec3d v) {
        if (v == null) return;

        float yaw = getYaw(v);
        float pitch = getPitch(v);

        client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround(), false));

        client.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(
                Hand.MAIN_HAND,
                0,
                yaw,
                pitch
        ));

        setLavaOnes();
    }

    private void setLavaOnes() {
        if (placeBlockStateHackFix && destroyerVec != null) {
            BlockHitResult ray = rayTrace(client.world, getPitch(destroyerVec), getYaw(destroyerVec), false);
            if (ray != null && ray.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = ray.getBlockPos().offset(ray.getSide());
                setBlockState(pos, Blocks.LAVA.getDefaultState());
            }
        }
        placeBlockStateHackFix = false;
    }

    private float getYaw(Vec3d v) {
        double x = v.x - playerVec.x;
        double z = v.z - playerVec.z;
        if (x == 0 && z == 0) return 0;
        return (float) (Math.atan2(x, z) * 180 / Math.PI * -1);
    }

    private float getPitch(Vec3d v) {
        double x = v.x - playerVec.x;
        double y = v.y - playerVec.y;
        double z = v.z - playerVec.z;

        return (float) (Math.atan(y / Math.sqrt(x * x + z * z)) * 180 / Math.PI * -1);
    }

    private Vec3d getBlockSpot(BlockPos pos, Vec3d vec, Box bb, boolean useLiquids) {
        double d0 = 1.0D / ((bb.maxX - bb.minX) * 2.0D + 1.0D);
        double d1 = 1.0D / ((bb.maxY - bb.minY) * 2.0D + 1.0D);
        double d2 = 1.0D / ((bb.maxZ - bb.minZ) * 2.0D + 1.0D);
        double d3 = (1.0D - Math.floor(1.0D / d0) * d0) / 2.0D;
        double d4 = (1.0D - Math.floor(1.0D / d2) * d2) / 2.0D;

        if (d0 >= 0.0D && d1 >= 0.0D && d2 >= 0.0D) {
            for (float f = 0.0F; f <= 1.0F; f = (float)((double)f + d0)) {
                for (float f1 = 0.0F; f1 <= 1.0F; f1 = (float)((double)f1 + d1)) {
                    for (float f2 = 0.0F; f2 <= 1.0F; f2 = (float)((double)f2 + d2)) {
                        double d5 = bb.minX + (bb.maxX - bb.minX) * (double)f;
                        double d6 = bb.minY + (bb.maxY - bb.minY) * (double)f1;
                        double d7 = bb.minZ + (bb.maxZ - bb.minZ) * (double)f2;
                        Vec3d spot = new Vec3d(d5 + d3, d6, d7 + d4);

                        float yaw = getYaw(spot);
                        float pitch = getPitch(spot);
                        BlockHitResult ray = rayTrace(client.world, pitch, yaw, useLiquids);

                        if (ray != null && ray.getType() == HitResult.Type.BLOCK && ray.getBlockPos().equals(pos)) {
                            return spot;
                        }
                    }
                }
            }
        }
        return null;
    }

    private BlockHitResult rayTrace(net.minecraft.world.World world, float pitch, float yaw, boolean useLiquids) {
        double d0 = playerVec.x;
        double d1 = playerVec.y;
        double d2 = playerVec.z;
        Vec3d start = new Vec3d(d0, d1, d2);

        float f2 = MathHelper.cos(-yaw * 0.017453292F - (float)Math.PI);
        float f3 = MathHelper.sin(-yaw * 0.017453292F - (float)Math.PI);
        float f4 = -MathHelper.cos(-pitch * 0.017453292F);
        float f5 = MathHelper.sin(-pitch * 0.017453292F);
        float f6 = f3 * f4;
        float f7 = f2 * f4;

        double d3 = 6.0D;
        Vec3d end = start.add((double)f6 * d3, (double)f5 * d3, (double)f7 * d3);

        RaycastContext.FluidHandling fluidHandling = useLiquids ?
                RaycastContext.FluidHandling.SOURCE_ONLY : RaycastContext.FluidHandling.NONE;

        try {
            return world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, fluidHandling, player));
        } catch (Exception e) {
            return null;
        }
    }

    private void setBlockState(BlockPos pos, BlockState blockState) {
        blockStateMap.put(pos, blockState);
    }

    private BlockState getBlockState(BlockPos pos) {
        return blockStateMap.getOrDefault(pos, client.world.getBlockState(pos));
    }
}
