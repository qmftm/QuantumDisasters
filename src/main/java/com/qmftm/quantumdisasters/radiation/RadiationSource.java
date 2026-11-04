package com.qmftm.quantumdisasters.radiation;

/** 지금 하늘에서 쏟아지는 방사선 세기를 알려주는 재해. */
public interface RadiationSource {

    /** 하늘이 뚫린 지표면(높이 64)에서의 선량률 (mSv/초). */
    double ambientRate();
}
