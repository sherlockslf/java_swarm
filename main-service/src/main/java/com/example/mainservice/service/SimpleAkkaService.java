package com.example.mainservice.service;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 简化的Akka服务示例
 * 演示如何在Spring Boot中集成Akka的基本用法
 */
@Slf4j
@Service
public class SimpleAkkaService {

    private final ActorSystem<String> actorSystem;

    public SimpleAkkaService() {
        // 创建一个简单的ActorSystem用于演示
        Config config = ConfigFactory.defaultReference();
        this.actorSystem = ActorSystem.create(Behaviors.empty(), "SimpleSystem", config);
        
        log.info("Akka ActorSystem initialized successfully");
    }
    
    /**
     * 获取ActorSystem实例（仅供演示）
     */
    public ActorSystem<String> getActorSystem() {
        return actorSystem;
    }
    
    /**
     * 关闭ActorSystem
     */
    public void shutdown() {
        actorSystem.terminate();
        log.info("Akka ActorSystem terminated");
    }
}