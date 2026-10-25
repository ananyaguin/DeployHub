package com.cloudflow.support;

import com.cloudflow.repository.DeploymentEventRepository;
import com.cloudflow.repository.DeploymentRepository;
import com.cloudflow.repository.DeploymentVersionRepository;
import com.cloudflow.repository.EmailVerificationTokenRepository;
import com.cloudflow.repository.ExecutionLogRepository;
import com.cloudflow.repository.ExecutionRepository;
import com.cloudflow.repository.NotificationDeliveryRepository;
import com.cloudflow.repository.NotificationPreferenceRepository;
import com.cloudflow.repository.PasswordResetTokenRepository;
import com.cloudflow.repository.TenantRepository;
import com.cloudflow.repository.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.mockito.Mockito.mock;

@Configuration
public class OfflineRepositoryConfiguration {

    @Bean
    @ConditionalOnMissingBean
    UserRepository userRepository() {
        return mock(UserRepository.class);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantRepository tenantRepository() {
        return mock(TenantRepository.class);
    }

    @Bean
    @ConditionalOnMissingBean
    DeploymentRepository deploymentRepository() {
        return mock(DeploymentRepository.class);
    }

    @Bean
    @ConditionalOnMissingBean
    DeploymentVersionRepository deploymentVersionRepository() {
        return mock(DeploymentVersionRepository.class);
    }

    @Bean
    @ConditionalOnMissingBean
    DeploymentEventRepository deploymentEventRepository() {
        return mock(DeploymentEventRepository.class);
    }

    @Bean
    @ConditionalOnMissingBean
    ExecutionRepository executionRepository() {
        return mock(ExecutionRepository.class);
    }

    @Bean
    @ConditionalOnMissingBean
    ExecutionLogRepository executionLogRepository() {
        return mock(ExecutionLogRepository.class);
    }

    @Bean
    @ConditionalOnMissingBean
    NotificationPreferenceRepository notificationPreferenceRepository() {
        return mock(NotificationPreferenceRepository.class);
    }

    @Bean
    @ConditionalOnMissingBean
    NotificationDeliveryRepository notificationDeliveryRepository() {
        return mock(NotificationDeliveryRepository.class);
    }

    @Bean
    @ConditionalOnMissingBean
    EmailVerificationTokenRepository emailVerificationTokenRepository() {
        return mock(EmailVerificationTokenRepository.class);
    }

    @Bean
    @ConditionalOnMissingBean
    PasswordResetTokenRepository passwordResetTokenRepository() {
        return mock(PasswordResetTokenRepository.class);
    }
}
