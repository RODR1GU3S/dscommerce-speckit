package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.dto.OwnOrderPageRequestDTO;
import jakarta.validation.Valid;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.LinkedHashMap;
import java.util.List;

/** Filters original decoded names before WebDataBinder can adapt markers/defaults/arrays. */
public final class OwnOrderPageArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == OwnOrderPageRequestDTO.class
                && parameter.hasParameterAnnotation(Valid.class)
                && !parameter.hasParameterAnnotation(ModelAttribute.class);
    }

    @Override
    public OwnOrderPageRequestDTO resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                                  NativeWebRequest request, WebDataBinderFactory binderFactory)
            throws Exception {
        var original = request.getParameterMap();
        var filtered = new LinkedHashMap<String, String[]>();
        for (var name : List.of("page", "size")) {
            if (original.containsKey(name)) {
                var values = original.get(name);
                if (values == null || values.length != 1 || values[0] == null) {
                    throw new ServletRequestBindingException("Invalid order query data");
                }
                filtered.put(name, values.clone());
            }
        }

        var input = new OwnOrderPageRequestDTO();
        var binder = binderFactory.createBinder(request, input, "ownOrderPageRequest");
        binder.setAllowedFields("page", "size");
        // bind(PropertyValues) never reads the original servlet request again.
        binder.bind(new MutablePropertyValues(filtered));
        binder.validate();
        if (binder.getBindingResult().hasErrors()) {
            throw new MethodArgumentNotValidException(parameter, binder.getBindingResult());
        }
        return input;
    }
}
