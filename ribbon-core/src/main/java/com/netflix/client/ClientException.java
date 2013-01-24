/*
*
* Copyright 2013 Netflix, Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
*/
package com.netflix.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;


public class ClientException extends Exception{
	/**
	 * 
	 */
	private static final long serialVersionUID = -7697654244064441234L;
	
	/**
     * define your error codes here
     * 
     */
    public enum ErrorType{
        GENERAL, 
        CONFIGURATION, 
        NUMBEROF_RETRIES_EXEEDED, 
        NUMBEROF_RETRIES_NEXTSERVER_EXCEEDED, 
        SOCKET_TIMEOUT_EXCEPTION, 
        READ_TIMEOUT_EXCEPTION,
        UNKNOWN_HOST_EXCEPTION,
        CONNECT_EXCEPTION,
        CLIENT_THROTTLED,
        SERVER_THROTTLED,
        NO_ROUTE_TO_HOST_EXCEPTION,
        CACHE_MISSING;
        
        static String getName(int errorCode){
            if (ErrorType.values().length >= errorCode){
                return ErrorType.values()[errorCode].name();
            }else{
                return "UNKNOWN ERROR CODE";
            }
        }
    }
    
    protected int errorCode;
    protected String message;
    protected Object errorObject;
    protected ErrorType errorType = ErrorType.GENERAL;

    public ClientException(String message) {
        this(0, message, null);
    }

    public ClientException(int errorCode) {
        this(errorCode, null, null);
    }

    public ClientException(int errorCode, String message) {
        this(errorCode, message, null);
    }

    public ClientException(Throwable chainedException) {
        this(0, null, chainedException);
    }

    public ClientException(String message, Throwable chainedException) {
        this(0, message, chainedException);
    }

    public ClientException(int errorCode, String message, Throwable chainedException) {
        super((message == null && errorCode != 0) ? ", code=" + errorCode + "->" + ErrorType.getName(errorCode): message,
              chainedException);
        this.errorCode = errorCode;
        this.message = message;
    }
    
    public ClientException(ErrorType error) {
        this(error.ordinal(), null, null);
        this.errorType = error;
    }

    public ClientException(ErrorType error, String message) {
        this(error.ordinal(), message, null);
        this.errorType = error;
    }
    
    public ClientException( ErrorType error, String message, Throwable chainedException) {
        super((message == null && error.ordinal() != 0) ? ", code=" + error.ordinal() + "->" + error.name() : message,
              chainedException);
        this.errorCode = error.ordinal();
        this.message = message;
        this.errorType = error;
    }

    public ErrorType getErrorType(){
        return errorType; 
    }

    public int getErrorCode() {
        return this.errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return this.message;
    }

    public void setErrorMessage(String msg) {
        this.message = msg;
    }


    public Object getErrorObject() {
        return this.errorObject;
    }

    public void setErrorObject(Object errorObject) {
        this.errorObject = errorObject;
    }

    /**
     * Return the message associated with such an exception.
     *
     * @return a message asssociated with current exception
     */

    public String getInternalMessage () {    
        return "{no message: " + errorCode + "}";
    }


    /**
     * Return the codes that are defined on a subclass of our class.
     *
     * @param clazz  a class that is a subclass of us.
     * @return a hashmap of int error codes mapped to the string names.
     */
    static public HashMap getErrorCodes ( Class clazz ) {

        HashMap map = new HashMap(23);

        // Use reflection to populte the erroCodeMap to have the reverse mapping
        // of error codes to symbolic names.

        Field flds[] = clazz.getDeclaredFields();

        for (int i = 0; i < flds.length; i++) {
            int mods = flds[i].getModifiers();

            if (Modifier.isFinal(mods) && Modifier.isStatic(mods) && Modifier.isPublic(mods)) {
                try {
                    map.put(flds[i].get(null), flds[i].getName());
                } catch (Throwable t) { // NOPMD
                    // ignore this.
                }
            }
        }
        return map;
    }    
}
