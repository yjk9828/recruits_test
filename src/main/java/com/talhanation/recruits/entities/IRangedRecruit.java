package com.talhanation.recruits.entities;

import com.talhanation.recruits.Main;
import net.minecraft.world.entity.monster.RangedAttackMob;

import java.util.Random;

public interface IRangedRecruit extends RangedAttackMob {
    Random random = new Random();
    static double getAngleHeightModifier(double distance, double heightDiff, double modifier) {
        if(distance >= 2000){
            return heightDiff * (1.15 * modifier);
        }
        else if(distance >= 1750){
            return heightDiff * (1.05 * modifier);
        }
        else if(distance >= 1500){
            return heightDiff * (0.6 * modifier);
        }

        else if(distance >= 1250){
            return heightDiff * (0.5 * modifier);
        }

        else if(distance >= 1000){
            return heightDiff * (0.4 * modifier);
        }
        else if(distance >= 750){
            return heightDiff * (0.3 * modifier);
        }
        else if(distance >= 500){
            return heightDiff * (0.2 * modifier);
        }
        else
            return 0;
    }

    static double getCrossbowAngleHeightModifier(double distance, double heightDiff) {
        if(distance >= 2500){
            return heightDiff * (0.3);
        }
        else if(distance >= 2000){
            return heightDiff * (0.25);
        }
        else if(distance >= 1750){
            return heightDiff * (0.2);
        }
        else if(distance >= 1500){
            return heightDiff * (0.15);
        }
        else if(distance >= 1250){
            return heightDiff * (0.125);
        }
        else if(distance >= 1000){
            return heightDiff * (0.1);
        }
        else if(distance >= 750){
            return heightDiff * (0.05);
        }
        else if(distance >= 500){
            return heightDiff * (0.025);
        }
        else
            return 0;
    }

    static double getAngleDistanceModifier(double distance, int x, int random) {
        double modifier = distance/x;
        return (modifier - IRangedRecruit.random.nextInt(-random, random)) /100;
    }

    static float getForceDistanceModifier(double distance, double base) {
        double modifier = 0;
        if(distance > 4000){
            modifier = base * 0.09;
        }
        else if(distance > 3750){
            modifier = base * 0.075;
        }
        else if(distance > 3500){
            modifier = base * 0.055;
        }
        else if(distance > 3000){
            modifier = base * 0.030;
        }
        else if(distance > 2500){
            modifier = base * 0.010;
        }

        return (float) modifier;
    }

// IRangedRecruit.java 덮어쓰기

    static double getCannonAngleDistanceModifier(double distanceSquared, int random) {
        // distanceSquared: 거리의 제곱 (예: 100블록 거리면 10000)
        double distance = Math.sqrt(distanceSquared); // 실제 거리로 변환
        
        // 기본 각도 (0에 가까울수록 직사, 높을수록 고각)
        // 마인크래프트의 angle은 보통 음수값이 위쪽(하늘)을 향함
        // -5.0 ~ -20.0 정도가 적당한 고각
        
        double angle = 0;

        if (distance > 250) { // 초장거리 (250블록 이상)
            angle = 25.0; // 극고각
        } else if (distance > 200) {
            angle = 20.0;
        } else if (distance > 150) {
            angle = 15.0;
        } else if (distance > 100) {
            angle = 10.0;
        } else if (distance > 50) {
            angle = 5.0;
        } else {
            angle = 2.0; // 근거리 직사
        }

        // 랜덤 오차 적용 (정밀도)
        double variance = IRangedRecruit.random.nextInt(-random, random) / 100.0;
        
        // 계산된 각도 + 오차 반환
        // (참고: 원본 코드의 리턴 방식인 distance/modifier 꼴을 유지하고 싶다면 아래 방식 사용)
        
        // 원본 스타일 유지하면서 거리별 최적값:
        double modifier = 0;
        if (distanceSquared > 40000) modifier = 80;  // 200블록 이상 -> 각도 높임
        else if (distanceSquared > 22500) modifier = 100; // 150블록
        else if (distanceSquared > 10000) modifier = 120; // 100블록
        else modifier = 150; // 근거리

        return (distanceSquared / modifier - IRangedRecruit.random.nextInt(-random, random)) / 100.0;
    }

    static double getCannonAngleHeightModifier(double distance, double heightDiff) {
        return heightDiff * (2.55);
    }
}
