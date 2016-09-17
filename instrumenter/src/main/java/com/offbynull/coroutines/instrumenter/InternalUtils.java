/*
 * Copyright (c) 2016, Kasra Faghihi, All rights reserved.
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
package com.offbynull.coroutines.instrumenter;

import java.util.List;
import org.apache.commons.lang3.Validate;

final class InternalUtils {
    private InternalUtils() {
        // do nothing
    }
    
    @SuppressWarnings("unchecked")
    static <T extends ContinuationPoint> T validateAndGetContinuationPoint(
            MethodAttributes attrs, int idx, Class<T> expectedType) {
        Validate.notNull(attrs);
        Validate.notNull(expectedType);
        Validate.isTrue(idx >= 0);
        
        List<ContinuationPoint> continuationPoints = attrs.getContinuationPoints();
        Validate.isTrue(idx < continuationPoints.size());
        
        ContinuationPoint continuationPoint = continuationPoints.get(idx);
        Validate.isTrue(expectedType.isAssignableFrom(continuationPoint.getClass()));
        
        return (T) continuationPoint;
    }
}
