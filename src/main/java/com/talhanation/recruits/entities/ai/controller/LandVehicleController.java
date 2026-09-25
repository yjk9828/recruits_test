package com.talhanation.recruits.entities.ai.controller;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.smallships.world.entity.ship.LandVehicle;
import com.talhanation.smallships.world.entity.ship.LandCannonEntity; // Import 추가됨
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class LandVehicleController {

    private final AbstractRecruitEntity driver;
    private LandVehicle vehicle;

    // [신규] 끼임 감지 타이머
    private int stuckTimer = 0;
    
    // [신규] 스캔 설정값
    private final double SCAN_DIST = 5.0D; 

    public LandVehicleController(AbstractRecruitEntity driver) {
        this.driver = driver;
    }

	public void tick() {
        if (driver.level().isClientSide) return;
        
        if (!(driver.getVehicle() instanceof LandVehicle)) {
            this.vehicle = null;
            return;
        }
        
        this.vehicle = (LandVehicle) driver.getVehicle();

        Entity controllingPassenger = vehicle.getControllingPassenger();
        // 내가 운전자가 아니면 제어권 포기
        if (controllingPassenger != driver) {
            return; 
        }

        // =============================================================
        // [★핵심 추가] 순양함(CruiserEntity) 전투 간섭 방지
        // =============================================================
        // 선장이 적을 조준(Aiming)하고 있을 때, 타는 배가 순양함이라면
        // 지상 주행 AI를 완전히 건너뛰어 SmallShipsController가 회전을 통제하게 둡니다.
        boolean isAiming = driver.getTarget() != null && driver.getTarget().isAlive();
        if (vehicle instanceof com.talhanation.smallships.world.entity.ship.CruiserEntity && isAiming) {
            return; 
        }
        // =============================================================

        // =============================================================
        // [수정됨] 대포(LandCannon) 탑승 시 이동 로직 완전 차단
        // =============================================================
        // 타겟 유무와 상관없이 대포는 스스로 이동(운전)하지 않습니다.
        // 회전(조준)은 오직 RecruitLandCannonAttackGoal에서만 담당하게 하여
        // 타겟이 없을 때 제자리에서 뱅글뱅글 도는 현상을 막습니다.
        if (vehicle instanceof LandCannonEntity) {
            stopVehicle(); // 모든 이동 입력(WASD)을 false로 초기화
            return;        // 드라이빙 로직 종료 (AttackGoal에 제어권 양도)
        }
        // =============================================================		

        // =============================================================
        // [신규] 1. 장애물 감지 및 능동 회피 (Path보다 우선 실행)
        // =============================================================
        if (performObstacleAvoidance()) {
            return; // 회피 기동 중이면 기존 경로 주행 로직은 건너뜀
        }
        // =============================================================

        Path path = driver.getNavigation().getPath();

        // 경로가 없거나 끝났으면 정지
        if (path == null || path.isDone()) {
            stopVehicle();
            return;
        }

        // --- 덩치 고려 Look-Ahead 로직 (속도 비례 가변 적용) ---
        double vehicleWidth = vehicle.getBbWidth(); 
        double speed = vehicle.getDeltaMovement().length();
        
        // 속도가 빠르면 더 멀리 봄 (기본 3.5 + 속도 보정)
        double minLookAheadDist = vehicleWidth * (3.5D + (speed * 5.0D)); 
        double minLookAheadSq = minLookAheadDist * minLookAheadDist;
        double stopDistSq = (vehicleWidth * 0.8D) * (vehicleWidth * 0.8D);

        Vec3 vehiclePos = vehicle.position();
        Vec3 targetPos = null;
        int distinctNextIndex = path.getNextNodeIndex();

        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
            Vec3 nodePos = path.getEntityPosAtNode(driver, i);
            double distSqr = nodePos.distanceToSqr(vehiclePos);

            if (i == path.getNodeCount() - 1) {
                targetPos = nodePos;
                distinctNextIndex = i;
                break;
            }

            if (distSqr > minLookAheadSq) {
                targetPos = nodePos;
                distinctNextIndex = i;
                break;
            }
        }

        if (targetPos == null) {
            if (path.getNodeCount() > 0) {
                targetPos = path.getEntityPosAtNode(driver, path.getNodeCount() - 1);
            } else {
                targetPos = vehiclePos;
            }
        }

        if (distinctNextIndex > path.getNextNodeIndex()) {
            for (int k = path.getNextNodeIndex(); k < distinctNextIndex; k++) {
                path.advance();
            }
        }

        if (path.isDone() || (path.getNextNodeIndex() >= path.getNodeCount() - 1 && vehiclePos.distanceToSqr(targetPos) < stopDistSq)) {
            stopVehicle();
            return;
        }

        // 개선된 운전 로직 호출
        driveTo(targetPos);
    }

    // =========================================================================
    // [신규] 장애물 회피 로직 (Scanner 기능을 여기에 통합)
    // =========================================================================
    private boolean performObstacleAvoidance() {
        // 1. 전방 스캔 (5칸 앞)
        int obstaclesFront = scanForward(5);
        
        // 전방이 뚫려있으면 회피할 필요 없음
        if (obstaclesFront == 0) {
            // 움직이고 있다면 끼임 타이머 리셋
            if (vehicle.getDeltaMovement().lengthSqr() > 0.01) stuckTimer = 0;
            return false;
        }

        // 2. 장애물 발견! -> 좌우 스캔 시작
        int obstaclesLeft = scanSide(true);
        int obstaclesRight = scanSide(false);

        boolean inputLeft = false;
        boolean inputRight = false;
        boolean inputForward = false;
        boolean inputBackward = false;

        // 3. 더 비어있는 쪽으로 회피 결정
        if (obstaclesLeft < obstaclesRight) {
            // 왼쪽이 더 쾌적함 -> 핸들 왼쪽 + 후진(공간확보)
            inputLeft = true;
            inputBackward = true; 
        } 
        else if (obstaclesLeft > obstaclesRight) {
            // 오른쪽이 더 쾌적함 -> 핸들 오른쪽 + 후진
            inputRight = true;
            inputBackward = true;
        } 
        else {
            // 양쪽 다 막힘 -> 닥치고 후진
            inputBackward = true;
            inputRight = true; // 핸들은 임의 방향
        }
        
        // [끼임 방지] 너무 오래(2초 이상) 제자리 걸음이면 강제 후진+핸들 꺾기
        if (vehicle.getDeltaMovement().lengthSqr() < 0.005) {
            stuckTimer++;
        }
        if (stuckTimer > 40) { 
            inputBackward = true;
            inputForward = false;
            // 갇혔을 때는 핸들을 반대로 꺾어보는 시도
            if(inputLeft) { inputLeft = false; inputRight = true; }
            else { inputLeft = true; inputRight = false; }
        }

        // 입력 적용
        vehicle.setInput(inputLeft, inputRight, inputForward, inputBackward);
        vehicle.setLeft(inputLeft);
        vehicle.setRight(inputRight);
        vehicle.setForward(inputForward);
        vehicle.setBackward(inputBackward);

        return true; // "나 지금 회피 중이야" (driveTo 실행하지 마)
    }

    // --- 내부 스캔 헬퍼 메서드들 ---

    private int scanForward(double range) {
        int obstacleCount = 0;
        Vec3 forward = vehicle.getForward().normalize();
        double widthOffset = vehicle.getBbWidth() * 0.5;

        // 정중앙
        if (hasObstacle(forward, range, 0)) obstacleCount++;
        // 좌측 헤드라이트 위치
        if (hasObstacle(forward, range, widthOffset)) obstacleCount++;
        // 우측 헤드라이트 위치
        if (hasObstacle(forward, range, -widthOffset)) obstacleCount++;
        
        return obstacleCount;
    }

    private int scanSide(boolean isLeft) {
        int obstacleCount = 0;
        Vec3 forward = vehicle.getForward().normalize();
        
        double startAngle = isLeft ? -15 : 15;
        double endAngle = isLeft ? -60 : 60;
        double step = isLeft ? -15 : 15;

        // 15도, 30도, 45도, 60도 방향으로 부채꼴 스캔
        for (double angle = startAngle; (isLeft ? angle >= endAngle : angle <= endAngle); angle += step) {
            Vec3 scanDir = rotateVector(forward, angle);
            if (hasObstacle(scanDir, SCAN_DIST, 0)) {
                obstacleCount++;
            }
        }
        return obstacleCount;
    }

    // [수정됨] 언덕 오르기 허용을 위해 스캔 높이 조정
    private boolean hasObstacle(Vec3 scanDirection, double dist, double sideOffset) {
        // [변경 포인트]
        // 기존: .add(0, 1.0, 0) -> 1칸 블록도 벽으로 인식함
        // 수정: .add(0, 1.6, 0) -> 1.5칸 이상의 높이만 장애물로 인식 (사람 눈높이)
        // 이렇게 하면 1칸(Block) 높이의 언덕은 레이저 밑으로 지나가서 감지되지 않음 = 그냥 밟고 올라감
        Vec3 startPos = vehicle.position().add(0, 1.6, 0);

        // 측면 오프셋(차폭) 적용
        if (sideOffset != 0) {
            Vec3 rightVec = scanDirection.yRot((float) Math.toRadians(-90)).normalize().scale(sideOffset);
            startPos = startPos.add(rightVec);
        }

        Vec3 targetPos = startPos.add(scanDirection.scale(dist));

        // RayTrace (블록만 감지)
        BlockHitResult hitResult = vehicle.level().clip(new ClipContext(
                startPos, 
                targetPos, 
                ClipContext.Block.COLLIDER, 
                ClipContext.Fluid.NONE, 
                vehicle
        ));

        // [추가 안전장치] 만약 감지된 블록이 있더라도, 그 블록이 "올라갈 수 있는 블록(Slabs, Stairs 등)"인지 체크할 수도 있으나,
        // Y=1.6 높이에서 걸린 거면 이미 2칸 높이의 벽일 확률이 높으므로 단순 BLOCK 체크로 충분함.
        return hitResult.getType() == HitResult.Type.BLOCK;
    }

    private Vec3 rotateVector(Vec3 forward, double angleOffset) {
        double angleRad = Math.toRadians(angleOffset);
        double rotatedX = forward.x * Math.cos(angleRad) - forward.z * Math.sin(angleRad);
        double rotatedZ = forward.x * Math.sin(angleRad) + forward.z * Math.cos(angleRad);
        return new Vec3(rotatedX, forward.y, rotatedZ);
    }
    // =========================================================================

    // [개선됨] 더 똑똑한 주행 로직 (후진 및 코너링 감속 포함)
    private void driveTo(Vec3 targetPos) {
        Vec3 forward = vehicle.getForward().normalize();
        Vec3 toTarget = targetPos.subtract(vehicle.position()).normalize();

        double angleDiff = Math.toDegrees(Math.atan2(toTarget.z, toTarget.x) - Math.atan2(forward.z, forward.x));
        angleDiff = Mth.wrapDegrees(angleDiff);

        boolean inputLeft = false;
        boolean inputRight = false;
        boolean inputForward = false;
        boolean inputBackward = false;
        double absAngle = Math.abs(angleDiff);

        // [개선] 목표가 뒤에 있으면(110도 이상) 후진으로 방향 전환
        if (absAngle > 110) {
            if (angleDiff > 0) inputLeft = true;
            else inputRight = true;
            inputBackward = true;
        } 
        else {
            // 정밀 조향 (10도 이상 차이날 때만 핸들 조작)
            if (angleDiff > 10.0) inputRight = true;
            else if (angleDiff < -10.0) inputLeft = true;

            // [개선] 코너링 감속 로직
            if (absAngle < 20.0) {
                // 각도가 거의 맞으면 풀악셀
                inputForward = true;
            } else if (absAngle < 45.0) {
                 // 각도가 좀 틀어졌으면 속도가 너무 느릴 때만 악셀 (관성 턴)
                 if (vehicle.getDeltaMovement().lengthSqr() < 0.05) {
                    inputForward = true; 
                }
            }
        }
        
        vehicle.setInput(inputLeft, inputRight, inputForward, inputBackward);
        vehicle.setLeft(inputLeft);
        vehicle.setRight(inputRight);
        vehicle.setForward(inputForward);
        vehicle.setBackward(inputBackward);
    }

    private void stopVehicle() {
        if (vehicle != null) {
            vehicle.setForward(false);
            vehicle.setBackward(false);
            vehicle.setLeft(false);
            vehicle.setRight(false);
            vehicle.setInput(false, false, false, false);

            vehicle.setSpeed(0.0F);
            Vec3 currentMotion = vehicle.getDeltaMovement();
            vehicle.setDeltaMovement(0, currentMotion.y, 0);
        }
    }
}