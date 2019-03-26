/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018>, <Raytheon BBN Technologies>
To be applied to the DCOMP/MAP Public Source Code Release dated 2018-04-19, with
the exception of the dcop implementation identified below (see notes).

Dispersed Computing (DCOMP)
Mission-oriented Adaptive Placement of Task and Data (MAP) 

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
BBN_LICENSE_END*/
/**
 * Copyright 2015 Raytheon BBN Technologies
 */
package com.bbn.map.appmgr.config;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.bbn.map.appmgr.exception.NotFoundException;
import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * API exception handler
 * 
 * @see https://spring.io/blog/2013/11/01/exception-handling-in-spring-mvc
 * @see https://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/web/bind/annotation/ExceptionHandler.html
 * 
 */
@ControllerAdvice
public class ApiExceptionHandler {
        
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ResponseStatus(HttpStatus.CONFLICT)  // 409
    @ExceptionHandler(DataIntegrityViolationException.class)
    public void handleConflict(Exception e) {
        // Nothing to do
        logger.info("Conflict issue.", e);
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Issue reading file contents")
    @ExceptionHandler(IOException.class)
    public void handleException(Exception e) {
        logger.info("Issue reading file contents", e);
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Error encountered. Typically you are missing a required key.")
    @ExceptionHandler(NullPointerException.class)
    public void handleNPEException(Exception e) {
        logger.info("Error encountered. Typically you are missing a required key.", e);
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Unsupported encoding encountered.")
    @ExceptionHandler(UnsupportedEncodingException.class)
    public void handleUnsupportedEncoding(Exception e) {
        logger.info("Unsupported encoding encountered.", e);
    }
    
    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Issue attempting to parse Json")
    @ExceptionHandler(JsonMappingException.class)
    public void handleJsonMappingException(Exception e) {
        logger.info("Unsupported encoding encountered.", e);
    }
    
    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Not Found")
    @ExceptionHandler(NotFoundException.class)
    public void handleNotFoundException(Exception e) {
        logger.info("Not Found", e);
    }
}
