package com.talhanation.recruits.util;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.entities.CaptainEntity;
import com.talhanation.smallships.world.entity.ship.LandBriggEntity;
import com.talhanation.smallships.world.entity.ship.LandCannonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse; // [추가] 말/당나귀 체크용
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FormationUtils {
    public static final double DEFAULT_SPACING = 1.75D;

    /**
     * [리팩토링] 탑승물에 따른 간격 보정 로직을 중앙화했습니다.
     * 모든 포메이션 메서드에서 이 함수를 호출하여 간격을 계산합니다.
     */
    private static double adjustSpacing(List<AbstractRecruitEntity> recruits, double baseSpacing) {
        for (AbstractRecruitEntity rec : recruits) {
            // 1. 배(Ship) 탑승 시: 간격 20배
            if (rec instanceof CaptainEntity captain && captain.smallShipsController.ship != null && captain.smallShipsController.ship.isCaptainDriver()) {
                return baseSpacing * 20.0D;
            }
            // 2. 육상 브릭(LandBrigg) 탑승 시: 간격 8배
            else if (rec.getVehicle() instanceof LandBriggEntity) {
                return baseSpacing * 8.0D;
            }
            // 3. 야포(LandCannon) 탑승 시: 간격 2.5배
            else if (rec.getVehicle() instanceof LandCannonEntity) {
                return baseSpacing * 2.5D;
            }
            // 4. [추가] 바닐라 말/당나귀/노새 탑승 시: 간격 2.5배
            else if (rec.getVehicle() instanceof AbstractHorse) {
                return baseSpacing * 2.5D;
            }
        }
        return baseSpacing;
    }

    public static Vec3 calculateLineBlockPosition(Vec3 targetPos, Vec3 linePos, int size, int index, Level level) {
        Vec3 toTarget = linePos.vectorTo(targetPos).normalize();
        Vec3 rotation = toTarget.yRot(3.14F/2).normalize();
        Vec3 pos;
        if(index == 0 || size/index > size/2)
            pos = linePos.lerp(linePos.add(rotation), index * 1.50);
        else
            pos = linePos.lerp(linePos.add(rotation.reverse()), index * 1.50);

        BlockPos blockPos = FormationUtils.getPositionOrSurface(
                level,
                new BlockPos((int) pos.x, (int) pos.y, (int) pos.z)
        );
        
        return new Vec3(blockPos.getX(), blockPos.getY(), blockPos.getZ());

    }
    public static void movementFormation(ServerPlayer player, List<AbstractRecruitEntity> recruits, Vec3 targetPos) {
        float yaw = player.getYRot();
        Vec3 forward = new Vec3(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
        lineFormation(forward, recruits, targetPos, 3, 2.0D);
    }

    public static void lineUpFormation(ServerPlayer player, List<AbstractRecruitEntity> recruits, Vec3 targetPos) {
        float yaw = player.getYRot();
        Vec3 forward = new Vec3(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
        lineFormation(forward, recruits, targetPos, 20, 1.75D);
    }

    public static void lineFormation(Vec3 forward, List<AbstractRecruitEntity> recruits, Vec3 targetPos, int maxInRow, double spacing) {
        // 간격 보정 적용
        spacing = adjustSpacing(recruits, spacing);

        Vec3 left = new Vec3(-forward.z, forward.y, forward.x);

        List<FormationPosition> possiblePositions = new ArrayList<>();

        for (int i = 0; i < recruits.size(); i++) {
            int row = i / maxInRow;
            int recruitsInCurrentRow = Math.min(maxInRow, recruits.size() - row * maxInRow);
            int positionInRow = i % maxInRow;

            double centerOffset = (recruitsInCurrentRow - 1) / 2.0;

            Vec3 basePos = targetPos.add(forward.scale(-3 * row));
            Vec3 offset = left.scale((positionInRow - centerOffset) * spacing);

            Vec3 recruitPos = basePos.add(offset);
            possiblePositions.add(new FormationPosition(recruitPos, true));
        }

        assignPositions(playerOrNull(recruits), recruits, possiblePositions);
    }

    public static void squareFormation(ServerPlayer player, List<AbstractRecruitEntity> recruits, Vec3 targetPos) {
        float yaw = player.getYRot();
        Vec3 forward = new Vec3(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
        squareFormation(forward, recruits, targetPos, 2.5);
    }

    public static void squareFormation(Vec3 forward, List<AbstractRecruitEntity> recruits, Vec3 targetPos, double spacing) {
        // 간격 보정 적용
        spacing = adjustSpacing(recruits, spacing);

        Vec3 left = new Vec3(-forward.z, forward.y, forward.x);

        int numRecruits = recruits.size();
        int sideLength = (int) Math.ceil(Math.sqrt(numRecruits));

        List<FormationPosition> possiblePositions = new ArrayList<>();

        for (int i = 0; i < numRecruits; i++) {
            int row = i / sideLength;
            int col = i % sideLength;

            Vec3 rowOffset = forward.scale(-row * spacing);
            Vec3 colOffset = left.scale((col - sideLength / 2F) * spacing);

            Vec3 recruitPos = targetPos.add(rowOffset).add(colOffset);
            possiblePositions.add(new FormationPosition(recruitPos, true));
        }

        assignPositions(null, recruits, possiblePositions);
    }

    public static void triangleFormation(ServerPlayer player, List<AbstractRecruitEntity> recruits, Vec3 targetPos) {
        float yaw = player.getYRot();
        Vec3 forward = new Vec3(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
        Vec3 left = new Vec3(-forward.z, forward.y, forward.x);

        // 간격 보정 적용 (기본 2.5)
        double spacing = adjustSpacing(recruits, 2.5);
        int numRecruits = recruits.size();

        List<FormationPosition> possiblePositions = new ArrayList<>();

        int index = 0;
        int rowCount = 1;
        while (index < numRecruits) {
            for (int positionInRow = 0; positionInRow < rowCount && index < numRecruits; positionInRow++, index++) {
                Vec3 basePos = targetPos.add(forward.scale(-3 * (rowCount - 1)));
                Vec3 offset = left.scale((positionInRow - (rowCount - 1) / 2F) * spacing);

                Vec3 recruitPos = basePos.add(offset);
                possiblePositions.add(new FormationPosition(recruitPos, true));
            }
            rowCount++;
        }

        assignPositions(player, recruits, possiblePositions);
    }

    public static void hollowCircleFormation(ServerPlayer player, List<AbstractRecruitEntity> recruits, Vec3 targetPos) {
        // 간격 보정 적용 (기본 2.5)
        double spacing = adjustSpacing(recruits, 2.5);
        int numRecruits = recruits.size();

        double radius = spacing * numRecruits / (2 * Math.PI); 
        List<FormationPosition> possiblePositions = new ArrayList<>();

        for (int i = 0; i < numRecruits; i++) {
            double angle = (2 * Math.PI / numRecruits) * i; 

            double x = targetPos.x + radius * Math.cos(angle);
            double z = targetPos.z + radius * Math.sin(angle);
            Vec3 recruitPos = new Vec3(x, targetPos.y, z);

            possiblePositions.add(new FormationPosition(recruitPos, true));
        }

        assignPositions(player, recruits, possiblePositions);
    }

    public static void circleFormation(ServerPlayer player, List<AbstractRecruitEntity> recruits, Vec3 targetPos) {
        // 간격 보정 적용 (기본 2.5)
        double spacing = adjustSpacing(recruits, 2.5);
        int numRecruits = recruits.size();

        int innerRingCount = Math.min(5, numRecruits); 
        int middleRingCount = Math.min(10, numRecruits - innerRingCount); 
        int outerRingCount = numRecruits - innerRingCount - middleRingCount; 

        double innerRadius = spacing * innerRingCount / (2 * Math.PI); 
        double middleRadius = spacing * middleRingCount / (2 * Math.PI); 
        double outerRadius = spacing * outerRingCount / (2 * Math.PI); 

        List<FormationPosition> possiblePositions = new ArrayList<>();

        // Inner Ring
        for (int i = 0; i < innerRingCount; i++) {
            double angle = (2 * Math.PI / innerRingCount) * i;
            double x = targetPos.x + innerRadius * Math.cos(angle);
            double z = targetPos.z + innerRadius * Math.sin(angle);
            possiblePositions.add(new FormationPosition(new Vec3(x, targetPos.y, z), true));
        }

        // Middle Ring
        for (int i = 0; i < middleRingCount; i++) {
            double angle = (2 * Math.PI / middleRingCount) * i;
            double x = targetPos.x + middleRadius * Math.cos(angle);
            double z = targetPos.z + middleRadius * Math.sin(angle);
            possiblePositions.add(new FormationPosition(new Vec3(x, targetPos.y, z), true));
        }

        // Outer Ring
        for (int i = 0; i < outerRingCount; i++) {
            double angle = (2 * Math.PI / outerRingCount) * i;
            double x = targetPos.x + outerRadius * Math.cos(angle);
            double z = targetPos.z + outerRadius * Math.sin(angle);
            possiblePositions.add(new FormationPosition(new Vec3(x, targetPos.y, z), true));
        }

        assignPositions(player, recruits, possiblePositions);
    }

    public static void hollowSquareFormation(ServerPlayer player, List<AbstractRecruitEntity> recruits, Vec3 targetPos) {
        float yaw = player.getYRot();
        Vec3 forward = new Vec3(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
        Vec3 left = new Vec3(-forward.z, forward.y, forward.x);

        int recruitsPerSide = Math.max(2, recruits.size() / 4); 
        // 간격 보정 적용 (기본 2.5)
        double spacing = adjustSpacing(recruits, 2.5);

        int totalRecruitsNeeded = recruitsPerSide * 4;
        if (totalRecruitsNeeded > recruits.size()) {
            recruitsPerSide = recruits.size() / 4;
            // totalRecruitsNeeded = recruitsPerSide * 4; 
        }

        List<FormationPosition> possiblePositions = new ArrayList<>();

        for (int row = 0; row < 2; row++) { 
            double offset = (spacing * recruitsPerSide) / 2.0;
            for (int i = 0; i < recruitsPerSide; i++) {
                double positionOffset = i * spacing - offset;

                possiblePositions.add(new FormationPosition(targetPos.add(forward.scale(-offset - row * spacing)).add(left.scale(positionOffset)), true));
                possiblePositions.add(new FormationPosition(targetPos.add(forward.scale(offset + row * spacing)).add(left.scale(positionOffset)), true));
                possiblePositions.add(new FormationPosition(targetPos.add(left.scale(-offset - row * spacing)).add(forward.scale(positionOffset)), true));
                possiblePositions.add(new FormationPosition(targetPos.add(left.scale(offset + row * spacing)).add(forward.scale(positionOffset)), true));
            }
        }

        assignPositions(player, recruits, possiblePositions);
    }

    public static void vFormation(ServerPlayer player, List<AbstractRecruitEntity> recruits, Vec3 targetPos) {
        float yaw = player.getYRot();
        Vec3 forward = new Vec3(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
        Vec3 left = new Vec3(-forward.z, forward.y, forward.x);

        // 간격 보정 적용 (기본 2.5)
        double spacing = adjustSpacing(recruits, 2.5);
        int recruitsPerWing = recruits.size() / 2;

        List<FormationPosition> possiblePositions = new ArrayList<>();

        for (int i = 0; i < recruitsPerWing; i++) {
            double offset = i * spacing;

            Vec3 rightWingPos = targetPos.add(forward.scale(offset)).add(left.scale(offset));
            possiblePositions.add(new FormationPosition(rightWingPos, true));

            Vec3 leftWingPos = targetPos.add(forward.scale(offset)).subtract(left.scale(offset));
            possiblePositions.add(new FormationPosition(leftWingPos, true));
        }

        if (recruits.size() % 2 != 0) {
            possiblePositions.add(new FormationPosition(targetPos, true));
        }

        assignPositions(player, recruits, possiblePositions);
    }

    // [헬퍼 메서드] 위치 할당 로직이 중복되어서 분리했습니다.
    private static void assignPositions(ServerPlayer player, List<AbstractRecruitEntity> recruits, List<FormationPosition> possiblePositions) {
        for (AbstractRecruitEntity recruit : recruits) {
            Vec3 pos = null;

            if (recruit.formationPos >= 0 && recruit.formationPos < possiblePositions.size() && possiblePositions.get(recruit.formationPos).isFree) {
                FormationPosition position = possiblePositions.get(recruit.formationPos);
                position.isFree = false;
                pos = position.position;
            } else {
                for (int i = 0; i < possiblePositions.size(); i++) {
                    FormationPosition position = possiblePositions.get(i);
                    if (position.isFree) {
                        pos = position.position;
                        recruit.formationPos = i; 
                        position.isFree = false;
                        break;
                    }
                }
            }

            if (pos != null) {
                BlockPos blockPos = FormationUtils.getPositionOrSurface(
                        recruit.getCommandSenderWorld(),
                        new BlockPos((int) pos.x, (int) pos.y, (int) pos.z)
                );

                recruit.setHoldPos(new Vec3(pos.x, blockPos.getY(), pos.z));
                if(player != null) recruit.ownerRot = player.getYRot(); // player가 있을 때만 회전 동기화
                recruit.setFollowState(3);
                recruit.isInFormation = true;
            }
        }
    }
    
    // 유틸: 플레이어 가져오기 (필요 시)
    private static ServerPlayer playerOrNull(List<AbstractRecruitEntity> recruits) {
        if(recruits.isEmpty()) return null;
        LivingEntity owner = recruits.get(0).getOwner();
        return owner instanceof ServerPlayer sp ? sp : null;
    }

    public static class FormationPosition{
        public Vec3 position;
        public boolean isFree;

        FormationPosition(Vec3 position, boolean isFree){
            this.position = position;
            this.isFree = isFree;
        }
    }

    public static Vec3 getCenterOfPositions(List<LivingEntity> recruits, ServerLevel level) {
        double sumX = 0;
        double sumY = 0;
        double sumZ = 0;

        for (LivingEntity recruit : recruits) {
            Vec3 pos = recruit.position();
            sumX += pos.x;
            sumY += pos.y;
            sumZ += pos.z;
        }

        double centerX = sumX / recruits.size();
        double centerY = sumY / recruits.size();
        double centerZ = sumZ / recruits.size();

        BlockPos blockPos = FormationUtils.getPositionOrSurface(
                level,
                new BlockPos((int) centerX, (int) centerY, (int) centerZ)
        );

        return new Vec3(centerX, blockPos.getY(), centerZ);
    }

    public static Vec3 getFarthestRecruitsCenter(List<AbstractRecruitEntity> recruits, ServerLevel level) {
        if (recruits.size() < 2) {
            return recruits.isEmpty() ? Vec3.ZERO : recruits.get(0).position();
        }

        AbstractRecruitEntity farthestRecruit1 = null;
        AbstractRecruitEntity farthestRecruit2 = null;
        double maxDistance = Double.MIN_VALUE;

        for (int i = 0; i < recruits.size() - 1; i++) {
            for (int j = i + 1; j < recruits.size(); j++) {
                double distance = recruits.get(i).distanceToSqr(recruits.get(j));
                if (distance > maxDistance) {
                    maxDistance = distance;
                    farthestRecruit1 = recruits.get(i);
                    farthestRecruit2 = recruits.get(j);
                }
            }
        }

        Vec3 pos1 = Objects.requireNonNull(farthestRecruit1).position();
        Vec3 pos2 = Objects.requireNonNull(farthestRecruit2).position();

        double centerX = (pos1.x + pos2.x) / 2.0;
        double centerY = (pos1.y + pos2.y) / 2.0;
        double centerZ = (pos1.z + pos2.z) / 2.0;

        BlockPos blockPos = FormationUtils.getPositionOrSurface(
                level,
                new BlockPos((int) centerX, (int) centerY, (int) centerZ)
        );

        return new Vec3(centerX, blockPos.getY(), centerZ);
    }

    public static Vec3 getGeometricMedian(List<AbstractRecruitEntity> recruits, ServerLevel level) {
        if (recruits.isEmpty()) {
            return Vec3.ZERO;
        }

        double sumX = 0, sumY = 0, sumZ = 0;
        for (AbstractRecruitEntity recruit : recruits) {
            Vec3 pos = recruit.position();
            sumX += pos.x;
            sumY += pos.y;
            sumZ += pos.z;
        }
        Vec3 currentGuess = new Vec3(sumX / recruits.size(), sumY / recruits.size(), sumZ / recruits.size());

        double tolerance = 1e-4;
        int maxIterations = 100;
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            double numeratorX = 0, numeratorY = 0, numeratorZ = 0;
            double denominator = 0;

            for (AbstractRecruitEntity recruit : recruits) {
                Vec3 pos = recruit.position();
                double distance = currentGuess.distanceTo(pos);

                if (distance < tolerance) {
                    continue;
                }

                double weight = 1 / distance;
                numeratorX += pos.x * weight;
                numeratorY += pos.y * weight;
                numeratorZ += pos.z * weight;
                denominator += weight;
            }

            Vec3 newGuess = new Vec3(numeratorX / denominator, numeratorY / denominator, numeratorZ / denominator);

            if (currentGuess.distanceTo(newGuess) < tolerance) {
                break;
            }

            currentGuess = newGuess;
        }

        BlockPos blockPos = FormationUtils.getPositionOrSurface(
                level,
                new BlockPos((int) currentGuess.x, (int) currentGuess.y, (int) currentGuess.z)
        );

        return new Vec3(currentGuess.x, blockPos.getY(), currentGuess.z);
    }

    public static BlockPos getPositionOrSurface(Level level, BlockPos pos) {
        boolean positionFree = true;
        for(int i = 0; i < 3; i++) {
            if(!level.getBlockState(pos.above(i)).isAir( )) {
                positionFree = false;
                break;
            }
        }

        return positionFree ? pos : new BlockPos(
                pos.getX(),
                level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, pos).getY(),
                pos.getZ()
        );
    }
}