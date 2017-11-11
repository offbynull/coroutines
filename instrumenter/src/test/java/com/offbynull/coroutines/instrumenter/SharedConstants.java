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
package com.offbynull.coroutines.instrumenter;

final class SharedConstants {
    public static final String SANITY_TEST = "SanityTest";
    public static final String DIFFERENT_STATES_TEST = "MultipleSuspendStatesTest";
    public static final String NORMAL_INVOKE_TEST = "NormalInvokeTest";
    public static final String STATIC_INVOKE_TEST = "StaticInvokeTest";
    public static final String INTERFACE_INVOKE_TEST = "InterfaceInvokeTest";
    public static final String RECURSIVE_INVOKE_TEST = "RecursiveInvokeTest";
    public static final String INHERITANCE_INVOKE_TEST = "InheritanceInvokeTest";
    public static final String RETURN_INVOKE_TEST = "ReturnInvokeTest";
    public static final String LONG_RETURN_INVOKE_TEST = "LongReturnInvokeTest";
    public static final String DOUBLE_RETURN_INVOKE_TEST = "DoubleReturnInvokeTest";
    public static final String LAMBDA_INVOKE_TEST = "LambdaInvokeTest";
    public static final String CONSTRUCTOR_INVOKE_TEST = "ConstructorInvokeTest";
    public static final String EXCEPTION_SUSPEND_TEST = "ExceptionSuspendTest";
    public static final String JSR_EXCEPTION_SUSPEND_TEST = "JsrExceptionSuspendTest";
    public static final String EXCEPTION_THROW_TEST = "ExceptionThrowTest";
    public static final String MONITOR_INVOKE_TEST = "MonitorInvokeTest";
    public static final String UNINITIALIZED_VARIABLE_INVOKE_TEST = "UninitializedVariableInvokeTest";
    public static final String PEERNETIC_FAILURE_TEST = "PeerneticFailureTest";
    public static final String NULL_TYPE_IN_LOCAL_VARIABLE_TABLE_INVOKE_TEST = "NullTypeInLocalVariableTableInvokeTest";
    public static final String NULL_TYPE_IN_OPERAND_STACK_INVOKE_TEST = "NullTypeInOperandStackInvokeTest";
    public static final String BASIC_TYPE_INVOKE_TEST = "BasicTypeInvokeTest";
    public static final String EXCEPTION_THEN_CONTINUE_INVOKE_TEST = "ExceptionThenContinueInvokeTest";
    public static final String EMPTY_CONTINUATION_POINT_INVOKE_TEST = "EmptyContinuationPointInvokeTest";
    public static final String COMPLEX_TEST = "ComplexTest";
    public static final String VERSION_TEST = "VersionTest";    
}
