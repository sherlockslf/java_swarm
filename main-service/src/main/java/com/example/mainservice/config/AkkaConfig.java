package com.example.mainservice.config;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.actor.typed.javadsl.Behaviors;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Akka Actor系统配置类
 * 负责创建和管理Akka ActorSystem实例
 */
@Configuration
public class AkkaConfig {

    /**
     * 创建Akka ActorSystem实例
     * 加载application-akka.conf配置文件并合并到默认配置中
     */
    @Bean
    @Primary
    public ActorSystem<Void> actorSystem() {
        // 加载自定义的Akka配置
        Config akkaConfig = ConfigFactory.parseResources("application-akka.conf");
        // 合并到默认配置中
        Config config = akkaConfig.withFallback(ConfigFactory.defaultReference());
        // 创建ActorSystem - 使用Behaviors.empty()作为根行为
        return ActorSystem.create(Behaviors.empty(), "MainSystem", config);
    }

    /**
     * 创建经典的ActorSystem适配器（用于兼容旧API）
     * 如果需要使用经典Actor API，可以使用此方法
     */
    @Bean
    public akka.actor.ActorSystem classicActorSystem(ActorSystem<?> typedActorSystem) {
        return Adapter.toClassic(typedActorSystem);
    }
}