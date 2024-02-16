package com.hmdp.utils;

/**
 *
 */
public interface ILock {
    boolean tryLock( long timeoutSet);
    void unLock();

}
