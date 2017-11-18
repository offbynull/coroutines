/*
 * Copyright (c) 2017, Kasra Faghihi, All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.offbynull.coroutines.user;

import com.offbynull.coroutines.user.SerializedState.Frame;
import com.offbynull.coroutines.user.SerializedState.FrameInterceptPoint;
import com.offbynull.coroutines.user.SerializedState.FrameModifier;
import com.offbynull.coroutines.user.SerializedState.FrameUpdatePoint;
import com.offbynull.coroutines.user.SerializedState.VersionedFrame;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class SerializationUtils {
    
    private SerializationUtils() {
        // do nothing
    }

    private static Frame findLoadableFrame(ClassLoader classLoader, VersionedFrame versionedFrame) {
        Frame[] possibleFrames = versionedFrame.getFrames();
        
        for (int i = 0; i < possibleFrames.length; i++) {
            Frame frame = possibleFrames[i];

            boolean found = MethodState.isValid(
                    classLoader,
                    frame.getClassName(),
                    frame.getMethodId(),
                    frame.getContinuationPointId());
            if (found) {
                return frame;
            }
        }

        return null;
    }

    private static Frame findUpdatableFrame(Map frameUpdaterMap, VersionedFrame versionedFrame) {
        Iterator entryIt = frameUpdaterMap.entrySet().iterator();
        while (entryIt.hasNext()) {
            Map.Entry entry = (Map.Entry) entryIt.next();
            FrameUpdatePointKey key = (FrameUpdatePointKey) entry.getKey();
            FrameUpdatePointValue value = (FrameUpdatePointValue) entry.getValue();

            Frame[] possibleFrames = versionedFrame.getFrames();
            for (int i = 0; i < possibleFrames.length; i++) {
                Frame frame = possibleFrames[i];

                String className = frame.getClassName();
                int methodId = frame.getMethodId();
                int continuationPointId = frame.getContinuationPointId();

                if (methodId == key.methodId && continuationPointId == key.continuationPointId && className.equals(key.className)) {
                    return frame;
                }
            }
        }

        return null;
    }

    private static Frame applyIntercept(Map interceptersMap, Frame frame, int mode) {
        String className = frame.getClassName();
        int methodId = frame.getMethodId();
        int continuationPointId = frame.getContinuationPointId();

        FrameUpdatePointKey key = new FrameUpdatePointKey(className, methodId, continuationPointId);
        FrameUpdatePointValue val = (FrameUpdatePointValue) interceptersMap.get(key);

        if (val == null) {
            return frame;
        }

        frame = val.frameModifier.modifyFrame(frame, mode);
        if (frame == null) {
            throw new IllegalStateException("Intercept frame modifier returned null");
        }
        
        if (!frame.getClassName().equals(key.className)
                || frame.getMethodId() != key.methodId
                || frame.getContinuationPointId() != key.continuationPointId) {
            throw new IllegalStateException("Intercept frame modifier updated the frame key: "
                    + "Frame classname=" + frame.getClassName() + ", "
                    + "Frame method ID=" + frame.getMethodId() + ", "
                    + "Frame continuation point ID=" + frame.getContinuationPointId());
        }

        return frame;
    }

    private static Frame applyUpdate(Map updatersMap, Frame frame, int mode) {
        String className = frame.getClassName();
        int methodId = frame.getMethodId();
        int continuationPointId = frame.getContinuationPointId();

        FrameUpdatePointKey key = new FrameUpdatePointKey(className, methodId, continuationPointId);
        FrameUpdatePointValue val = (FrameUpdatePointValue) updatersMap.get(key);

        if (val == null) {
            return frame;
        }

        frame = val.frameModifier.modifyFrame(frame, mode);
        if (frame == null) {
            throw new IllegalStateException("Update frame modifier returned null");
        }

        if (frame.getClassName().equals(key.className)
                && frame.getMethodId() == key.methodId
                && frame.getContinuationPointId() == key.continuationPointId) {
            throw new IllegalStateException("Update frame modifier didn't update the frame key: "
                    + "Frame classname=" + frame.getClassName() + ", "
                    + "Frame method ID=" + frame.getMethodId() + ", "
                    + "Frame continuation point ID=" + frame.getContinuationPointId());
        }

        return frame;
    }

    private static Frame[] chainUpdatesOnFrame(Map updatersMap, Map interceptersMap, Frame frame, int mode) {
        LinkedHashSet ret = new LinkedHashSet(); // ordered and unique

        while (true) {
            Frame start = frame;

            Frame intercepted = applyIntercept(interceptersMap, start, mode); // will return input if no intercepter
            Frame updated = applyUpdate(updatersMap, intercepted, mode);      // will return input if no updater
            
            // The following cases have been explicitly written out and documented because this it will be confusing when you come back to
            // this in the future.

            if (intercepted == start           /* no intercept */
                    && updated == start        /* no update */) {
                // no modifications took place -- there's no where else to go from here so add it and break out of loop
                ret.add(start); // add the original, if alreayd exists it won't be duplicated because this is a linked hash set
                break;
            } else if (intercepted != start    /* yes intercept */
                    && updated == intercepted  /* no update */) {
                // frame was intercepted but not updated -- remember that interception means that the frame key (classname, methodid, and
                // continuationpoint id) doesn't change, so even though the frame was technically updated we need to leave the loop because
                // if we loop again it'll call the intercepter again on the same frame, which may result in bad changes being made
                ret.add(intercepted); // add the intercepted
                break;
            } else if (intercepted == start    /* no intercept */
                    && updated != intercepted  /* yes update */) {
                // frame was not intercepted but did update -- remember that updating means that the frame key (classname, methodid, and
                // continuationpoint id) DOES change, which means we must loop again because there might be a new interceptor and/or for
                // this new frame key
                ret.add(start); // add the frame prior to updating -- Need this because if this is the first update, the version before the
                                // update needs to be added first. But, if this isn't the first update, it's fine because we're using a
                                // LinkedHashSet which maintains order but discards duplicates.
                ret.add(updated); // add the updated
                frame = updated;  // continue from the latest save
                continue;
            } else if (intercepted != start    /* yes intercept */
                    && updated != intercepted  /* yes update */) {
                // frame was both intercepted and updated -- remember that updating means that the frame key (classname, methodid, and
                // continuationpoint id) DOES change, which means we must loop again because there might be a new interceptor and/or for
                // this new frame key
                ret.add(intercepted); // add the frame before the updating... but since we intercepted before we updated, add "intercepted"
                                      // instead of "started" -- Need this because if this is the first update, the version before the
                                      // update needs to be added first. But, if this isn't the first update, it's fine because we're using
                                      // a LinkedHashSet which maintains order but discards duplicates.
                ret.add(updated); // add the updated
                frame = updated;  // continue from the latest save
                continue;
            }
        }
        
        Frame[] retArray = new Frame[ret.size()];
        ret.toArray(retArray);
        
        return retArray;
    }









    static Frame calculateCorrectFrameVersion(ClassLoader classLoader, Map updatersMap, Map interceptersMap,
            VersionedFrame versionedFrame) {
        Frame loadableFrame = SerializationUtils.findLoadableFrame(classLoader, versionedFrame);
        Frame updatableFrame = findUpdatableFrame(updatersMap, versionedFrame);

        if (loadableFrame != null && updatableFrame != null) {
            throw new IllegalStateException("Loadable frame detected, but updatable frame also exists");
        } else if (loadableFrame == null && updatableFrame == null) {
            throw new IllegalStateException("No loadable frame or updatable frame detected");
        }


        // At this point one of them will be non-null, so load the non-null one as the frame to operate on
        Frame frame = loadableFrame != null ? loadableFrame : updatableFrame;


        // Call any interceptors andd updaters on frame to get the final loadable frame.
        Frame[] frameUpdateChain = chainUpdatesOnFrame(updatersMap, interceptersMap, frame, FrameModifier.READ);
        frame = frameUpdateChain[frameUpdateChain.length - 1]; // get last (there will always be atleast 1 frame in here)


        // This is the final frame, so make sure that it's loadable.
        boolean found = MethodState.isValid(
                classLoader,
                frame.getClassName(),
                frame.getMethodId(),
                frame.getContinuationPointId());
        if (!found) {
            throw new IllegalStateException("Updated to an unloadable method ID: "
                    + "Frame classname=" + frame.getClassName() + ", "
                    + "Frame method ID=" + frame.getMethodId() + ", "
                    + "Frame continuation point ID=" + frame.getContinuationPointId());
        }


        // Return it
        return frame;
    }

    static VersionedFrame calculateAllPossibleFrameVersions(ClassLoader classLoader, Map updatersMap, Map interceptersMap, Frame frame) {
        // Ensure frame is for a method that we can save (sanity check)
        boolean found = MethodState.isValid(
                classLoader,
                frame.getClassName(),
                frame.getMethodId(),
                frame.getContinuationPointId());
        if (!found) {
            throw new IllegalArgumentException("Attempting to write a frame for a non-existant method: "
                    + "Frame classname=" + frame.getClassName() + ", "
                    + "Frame method ID=" + frame.getMethodId() + ", "
                    + "Frame continuation point ID=" + frame.getContinuationPointId());
        }
        
        
        // We found an updatable frame. Chain updates to get it to a final loadable state.
        Frame[] frameUpdateChain = chainUpdatesOnFrame(updatersMap, interceptersMap, frame, FrameModifier.WRITE);
        return new VersionedFrame(frameUpdateChain);
    }

    

    
    
    
    
    

    
    

    static boolean findDuplicates(Frame[] frames) {
        Set frameKeys = new HashSet();
        for (int i = 0; i < frames.length; i++) {
            frameKeys.add(new FrameKey(frames[i]));
        }
        return frames.length != frameKeys.size();
    }
    
    private static final class FrameKey {
        private final String className;
        private final int methodId;
        private final int continuationPointId;

        FrameKey(Frame frame) {
            className = frame.getClassName();
            methodId = frame.getMethodId();
            continuationPointId = frame.getContinuationPointId();
        }

        public int hashCode() {
            int hash = 5;
            hash = 37 * hash + (this.className != null ? this.className.hashCode() : 0);
            hash = 37 * hash + this.methodId;
            hash = 37 * hash + this.continuationPointId;
            return hash;
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final FrameKey other = (FrameKey) obj;
            if (this.methodId != other.methodId) {
                return false;
            }
            if (this.continuationPointId != other.continuationPointId) {
                return false;
            }
            if ((this.className == null) ? (other.className != null) : !this.className.equals(other.className)) {
                return false;
            }
            return true;
        }

    }
    
    
    
    
    
    
    
    
    
    
    
    static void populateUpdatesMapAndInterceptsMap(
            Map updatersMap, FrameUpdatePoint[] frameUpdatePoints,
            Map interceptersMap, FrameInterceptPoint[] frameInterceptPoints) {
        for (int i = 0; i < frameUpdatePoints.length; i++) {
            FrameUpdatePoint frameUpdatePoint = frameUpdatePoints[i];
            if (frameUpdatePoint == null) {
                throw new NullPointerException();
            }

            FrameUpdatePointKey key = frameUpdatePoint.toKey();
            FrameUpdatePointValue value = frameUpdatePoint.toValue();
            
            Object oldKey = updatersMap.put(key, value);
            if (oldKey != null) {
                throw new IllegalArgumentException("Frame update point for identifier already exists: "
                        + frameUpdatePoint.toString());
            }
        }

        for (int i = 0; i < frameInterceptPoints.length; i++) {
            FrameInterceptPoint frameInterceptPoint = frameInterceptPoints[i];
            if (frameInterceptPoint == null) {
                throw new NullPointerException();
            }

            FrameUpdatePointKey key = frameInterceptPoint.toKey();
            FrameUpdatePointValue value = frameInterceptPoint.toValue();
            
            Object oldKey = interceptersMap.put(key, value);
            if (oldKey != null) {
                throw new IllegalArgumentException("Frame intercept point for identifier already exists: "
                        + frameInterceptPoint.toString());
            }
        }
    }

    static final class FrameUpdatePointKey {

        private final String className;
        private final int methodId;
        private final int continuationPointId;

        FrameUpdatePointKey(String className, int methodId, int continuationPointId) {
            if (className == null) {
                throw new NullPointerException();
            }

            this.className = className;
            this.methodId = methodId;
            this.continuationPointId = continuationPointId;
        }

        public int hashCode() {
            int hash = 7;
            hash = 71 * hash + (this.className != null ? this.className.hashCode() : 0);
            hash = 71 * hash + this.methodId;
            hash = 71 * hash + this.continuationPointId;
            return hash;
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final FrameUpdatePointKey other = (FrameUpdatePointKey) obj;
            if (this.methodId != other.methodId) {
                return false;
            }
            if (this.continuationPointId != other.continuationPointId) {
                return false;
            }
            if ((this.className == null) ? (other.className != null) : !this.className.equals(other.className)) {
                return false;
            }
            return true;
        }
    }
    
    static final class FrameUpdatePointValue {
        private final FrameModifier frameModifier;

        FrameUpdatePointValue(FrameModifier frameModifier) {
            if (frameModifier == null) {
                throw new NullPointerException();
            }
            this.frameModifier = frameModifier;
        }        
    }
}
