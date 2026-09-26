package com.devsuperior.dscommerce.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.Repository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class UserAccountRepositoryTest {

    private static final String REPOSITORY_BEAN_NAME = "userAccountRepository";
    private static final String REPOSITORY_INTERFACE_NAME = "UserAccountRepository";

    @Autowired
    private ApplicationContext applicationContext;

    private JpaRepository<Object, Object> repository;
    private Method findByName;
    private Class<?> accountType;

    @BeforeEach
    void discoverRepositoryThroughSpringContext() throws Exception {
        Map<String, Repository> repositoryBeans = applicationContext.getBeansOfType(Repository.class);

        assertThat(repositoryBeans)
                .as("Spring context must expose the UserAccount repository bean")
                .containsKey(REPOSITORY_BEAN_NAME);

        Object repositoryBean = repositoryBeans.get(REPOSITORY_BEAN_NAME);
        Class<?> repositoryInterface = Arrays.stream(repositoryBean.getClass().getInterfaces())
                .filter(type -> type.getSimpleName().equals(REPOSITORY_INTERFACE_NAME))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "The discovered bean must implement UserAccountRepository"));

        findByName = repositoryInterface.getMethod("findByName", String.class);
        accountType = resolveOptionalElementType(findByName);

        assertThat(repositoryBean)
                .as("The UserAccount repository must provide JPA persistence operations")
                .isInstanceOf(JpaRepository.class);
        repository = castJpaRepository(repositoryBean);
    }

    @Test
    void findByNameShouldReturnTheUniqueAccountForTheExactName() {
        Object account = persistAccount("unique-exact-name");

        Optional<?> result = invokeFindByName("unique-exact-name");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow()).isSameAs(account);
        assertThat(readName(result.orElseThrow())).isEqualTo("unique-exact-name");
    }

    @Test
    void nameShouldBeUnique() {
        persistAccount("unique-name");

        assertThatThrownBy(() -> persistAccount("unique-name"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByNameShouldRequireAnExactName() {
        persistAccount("exact-name");

        assertThat(invokeFindByName("exact-name")).isPresent();
        assertThat(invokeFindByName("exact-nam")).isEmpty();
        assertThat(invokeFindByName(" exact-name ")).isEmpty();
    }

    @Test
    void findByNameShouldBeCaseSensitive() {
        persistAccount("CaseSensitiveName");

        assertThat(invokeFindByName("CaseSensitiveName")).isPresent();
        assertThat(invokeFindByName("casesensitivename")).isEmpty();
        assertThat(invokeFindByName("CASESENSITIVENAME")).isEmpty();
    }

    private Object persistAccount(String name) {
        Object account = instantiateAccount();
        ReflectionTestUtils.setField(account, "name", name);
        ReflectionTestUtils.setField(account, "passwordHash", "$2a$10$repository.characterization.hash.value");
        return repository.saveAndFlush(account);
    }

    private Object instantiateAccount() {
        try {
            var constructor = accountType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("UserAccount must provide a JPA no-argument constructor", exception);
        }
    }

    private Optional<?> invokeFindByName(String name) {
        try {
            Object result = findByName.invoke(repository, name);
            assertThat(result).isInstanceOf(Optional.class);
            return (Optional<?>) result;
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError("findByName failed", cause);
        } catch (IllegalAccessException exception) {
            throw new AssertionError("findByName must be callable", exception);
        }
    }

    private static Class<?> resolveOptionalElementType(Method method) {
        assertThat(method.getGenericReturnType()).isInstanceOf(ParameterizedType.class);
        ParameterizedType returnType = (ParameterizedType) method.getGenericReturnType();
        assertThat(returnType.getRawType()).isEqualTo(Optional.class);
        assertThat(returnType.getActualTypeArguments()).hasSize(1);
        assertThat(returnType.getActualTypeArguments()[0]).isInstanceOf(Class.class);
        return (Class<?>) returnType.getActualTypeArguments()[0];
    }

    private static String readName(Object account) {
        Object name = ReflectionTestUtils.getField(account, "name");
        assertThat(name).isInstanceOf(String.class);
        return (String) name;
    }

    @SuppressWarnings("unchecked")
    private static JpaRepository<Object, Object> castJpaRepository(Object repositoryBean) {
        return (JpaRepository<Object, Object>) repositoryBean;
    }
}
