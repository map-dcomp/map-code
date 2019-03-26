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
package com.bbn.map.appmgr;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Date;
import java.util.Arrays;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.xml.datatype.XMLGregorianCalendar;

import org.neo4j.ogm.session.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.data.neo4j.transaction.Neo4jTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import com.bbn.map.appmgr.config.Constants;
import com.google.common.base.Predicates;

/*
 * MAP Application Manager configuration and main class
 * 
 * @author jmcettrick
 * 
 */
@EnableSwagger2
@SpringBootApplication
@Configuration
@EnableNeo4jRepositories("com.bbn.map.appmgr.repository")
@EnableTransactionManagement
@SpringBootConfiguration
public class ApplicationManagerMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationManagerMain.class);
    
    @Bean
    public SessionFactory sessionFactory() {
        // with domain entity base package(s)
        return new SessionFactory("com.bbn.map.common.value");
    }

    @Bean
    public Neo4jTransactionManager transactionManager() throws Exception {
        return new Neo4jTransactionManager(sessionFactory());
    }

    @Inject
    private Environment env;

    @PostConstruct
    public void initApplication() {
        if (env.getActiveProfiles().length == 0) {
            LOGGER.warn("No configured profile, using default configuration");
        } else {
            LOGGER.info("Active profile(s) : {}", Arrays.toString(env.getActiveProfiles()));
        }                               
    }
    
    @Bean
    public Docket applicationManagerApi() {
        return new Docket(DocumentationType.SWAGGER_2)
                .directModelSubstitute(XMLGregorianCalendar.class, Date.class)
                .groupName("map")    
                .apiInfo(apiInfo())
                .select()
                .paths(Predicates.not(PathSelectors.regex("/error"))) // Exclude Spring error controllers from swagger
                .build();
    }

    public ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("MAP Application Manager")
                .description("Manage MAP application specifications, profiles and dependencies")
                .termsOfServiceUrl("")
                .version("0.0.1")
                .build();
    }
    
//    /**
//     * http://stackoverflow.com/a/31748398/122441 until https://jira.spring.io/browse/DATAREST-573
//     * @return
//     */
//    @Bean
//    public FilterRegistrationBean corsFilter() {
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        CorsConfiguration config = new CorsConfiguration();
//        config.setAllowCredentials(true);
//        config.addAllowedOrigin("*");
//        config.addAllowedHeader("*");
//        config.addAllowedMethod("OPTIONS");
//        config.addAllowedMethod("HEAD");
//        config.addAllowedMethod("GET");
//        config.addAllowedMethod("PUT");
//        config.addAllowedMethod("POST");
//        config.addAllowedMethod("DELETE");
//        config.addAllowedMethod("PATCH");
//        source.registerCorsConfiguration("/**", config);
//        // return new CorsFilter(source);
//        final FilterRegistrationBean bean = new FilterRegistrationBean(new CorsFilter(source));
//        bean.setOrder(0);
//        return bean;
//    }
    
    public static void main(String[] args) throws UnknownHostException {
        SpringApplication app = new SpringApplication(ApplicationManagerMain.class);
        SimpleCommandLinePropertySource source = new SimpleCommandLinePropertySource(args);
        addDefaultProfile(app, source);
        Environment env = app.run(args).getEnvironment();
        LOGGER.info(
                "\n-------------------------------------------------------------\n"
                + "--                 MAP Application Manager                 --\n"
                + "-------------------------------------------------------------\n"
                + "  Configuration:\t{}\n"
                + "  SWAGGER:\thttp://{}:{}/swagger-ui.html#/\n"
                + "  API:\thttp://{}:{}/api/v2\n"
                + "-------------------------------------------------------------",
                env.getActiveProfiles(),
                InetAddress.getLocalHost().getHostAddress(), env.getProperty("server.port"), 
                InetAddress.getLocalHost().getHostAddress(), env.getProperty("server.port"), 
                InetAddress.getLocalHost().getHostAddress(), env.getProperty("server.port") 
                );
        
//        + "  Actuator:\thttp://{}:{}/actuator\n"  // if we include the actuator later
           
    }

    /**
     * use "dev" profile as default
     */
    public static void addDefaultProfile(SpringApplication app, SimpleCommandLinePropertySource source) {
        if (!source.containsProperty("spring.profiles.active")
                && !System.getenv().containsKey("SPRING_PROFILES_ACTIVE")) {
            app.setAdditionalProfiles(Constants.PROFILE_DEV);
        }
    }
}
