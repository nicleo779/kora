package com.nicleo.kora.spring.boot.autoconfigure;

import com.nicleo.kora.core.annotation.KoraScan;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

final class KoraMapperBeanDefinitionRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor,
        ResourceLoaderAware, EnvironmentAware, PriorityOrdered {

    private ResourceLoader resourceLoader;
    private Environment environment;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        Set<String> mapperPackages = new LinkedHashSet<>();
        String[] beanDefinitionNames = registry.getBeanDefinitionNames();
        ClassLoader classLoader = resourceLoader != null ? resourceLoader.getClassLoader() : ClassUtils.getDefaultClassLoader();
        for (String beanDefinitionName : beanDefinitionNames) {
            BeanDefinition beanDefinition = registry.getBeanDefinition(beanDefinitionName);
            String beanClassName = beanDefinition.getBeanClassName();
            if (!StringUtils.hasText(beanClassName) || !ClassUtils.isPresent(beanClassName, classLoader)) {
                continue;
            }
            Class<?> beanClass = ClassUtils.resolveClassName(beanClassName, classLoader);
            KoraScan koraScan = beanClass.getAnnotation(KoraScan.class);
            if (koraScan == null) {
                continue;
            }
            for (String mapperPackage : koraScan.mapper()) {
                if (StringUtils.hasText(mapperPackage)) {
                    mapperPackages.add(mapperPackage);
                }
            }
        }
        if (mapperPackages.isEmpty()) {
            return;
        }
        ClassPathScanningCandidateComponentProvider scanner = new InterfaceScanner(false, environment);
        if (resourceLoader != null) {
            scanner.setResourceLoader(resourceLoader);
        }
        for (String mapperPackage : mapperPackages) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(mapperPackage)) {
                registerMapperBean(candidate.getBeanClassName(), registry, classLoader);
            }
        }
    }

    private void registerMapperBean(String mapperInterfaceName, BeanDefinitionRegistry registry, ClassLoader classLoader) {
        if (!StringUtils.hasText(mapperInterfaceName)) {
            return;
        }
        String implClassName = mapperInterfaceName + "Impl";
        if (!ClassUtils.isPresent(implClassName, classLoader)) {
            return;
        }
        String beanName = mapperInterfaceName;
        if (registry.containsBeanDefinition(beanName) || registry.containsBeanDefinition("scopedTarget." + beanName)) {
            return;
        }
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(implClassName);
        builder.setScope(BeanDefinition.SCOPE_PROTOTYPE);
        builder.addConstructorArgReference("sqlSession");
        BeanDefinitionHolder proxyHolder = org.springframework.aop.scope.ScopedProxyUtils.createScopedProxy(
                new BeanDefinitionHolder(builder.getBeanDefinition(), beanName), registry, false);
        registry.registerBeanDefinition(proxyHolder.getBeanName(), proxyHolder.getBeanDefinition());
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static final class InterfaceScanner extends ClassPathScanningCandidateComponentProvider {
        private InterfaceScanner(boolean useDefaultFilters, Environment environment) {
            super(useDefaultFilters, environment);
            addIncludeFilter((metadataReader, metadataReaderFactory) -> metadataReader.getClassMetadata().isInterface());
        }

        @Override
        protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
            return beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata().isIndependent();
        }
    }
}
