/*
 * Copyright (c) 2015, Kasra Faghihi, All rights reserved.
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

/**
 * Exception that indicates that a problem occurred during the execution of a coroutine.
 * @author Kasra Faghihi
 */
public class CoroutineException extends RuntimeException {
    
    private Throwable nested;
    
    CoroutineException(String message, Throwable cause) {
        // super(message, cause); NOT SUPPORTED IN 1.2! NEED AT LEAST 1.4 FOR THIS CONSTRUCTOR
        super(message);
        nested = cause;
    }

    /**
     * Since chained exceptions aren't supported in Java1.2, this is how {@link CoroutineException} passes back the underlying exception.
     * @return underlying exception
     */
    public final Throwable getNested() {
        return nested;
    }
}
