package com.talhanation.recruits.entities;

import net.minecraft.core.BlockPos;

public interface IStrategicFire {
    // Setter
    void setShouldStrategicFire(boolean should);
    void setStrategicFirePos(BlockPos blockpos);

    // [수정] Getter 메서드 추가 (오류 해결 핵심)
    boolean getShouldStrategicFire();
    BlockPos getStrategicFirePos();
}