package com.devsuperior.dscommerce.config;

import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

/** ContextRunner needs the test-component scan exclusion normally installed by SpringBootTest. */
final class TestComponentExclusion implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        context.getBeanFactory().registerSingleton("runnerTestComponentFilter", new TestComponentFilter());
    }

    private static final class TestComponentFilter extends TypeExcludeFilter {
        @Override
        public boolean match(MetadataReader reader, MetadataReaderFactory factory) {
            var metadata = reader.getAnnotationMetadata();
            return metadata.hasAnnotation("org.springframework.boot.test.context.TestConfiguration")
                    || metadata.hasAnnotation("org.springframework.boot.test.context.TestComponent")
                    || metadata.hasMetaAnnotation("org.springframework.boot.test.context.TestComponent");
        }

        @Override
        public boolean equals(Object other) { return other instanceof TestComponentFilter; }

        @Override
        public int hashCode() { return TestComponentFilter.class.hashCode(); }
    }
}
