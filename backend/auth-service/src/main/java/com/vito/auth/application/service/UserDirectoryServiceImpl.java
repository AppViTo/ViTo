package com.vito.auth.application.service;

import com.vito.auth.domain.exception.InvalidRequestException;
import com.vito.auth.domain.model.User;
import com.vito.auth.domain.model.UserSummary;
import com.vito.auth.domain.port.inbound.UserDirectoryService;
import com.vito.auth.domain.port.outbound.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDirectoryServiceImpl implements UserDirectoryService {

    private final UserRepository userRepository;

    @Override
    public List<UserSummary> search(String query, String requesterId, Integer page, Integer size) {
        String normalized = SearchNormalizer.query(query);
        int length = SearchNormalizer.length(normalized);
        if (length < MIN_QUERY_LENGTH || length > MAX_QUERY_LENGTH) {
            throw InvalidRequestException.queryOutOfRange(MIN_QUERY_LENGTH, MAX_QUERY_LENGTH);
        }
        int pageNumber = page != null ? page : 0;
        int pageSize = size != null ? size : DEFAULT_SEARCH_SIZE;
        if (pageNumber < 0) {
            throw InvalidRequestException.invalidPagination("page", "Debe ser 0 o mayor");
        }
        if (pageSize < 1 || pageSize > MAX_SEARCH_SIZE) {
            throw InvalidRequestException.invalidPagination("size", "Entre 1 y " + MAX_SEARCH_SIZE);
        }

        return userRepository.search(normalized, requesterId, pageNumber, pageSize).stream()
                .map(UserSummary::from)
                .toList();
    }

    @Override
    public List<UserSummary> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > MAX_IDS) {
            throw InvalidRequestException.idsOutOfRange(MAX_IDS);
        }
        Set<String> unique = new LinkedHashSet<>(ids);

        // Una sola consulta; el orden de la respuesta es el pedido, no el de Mongo
        Map<String, User> found = userRepository.findAllByIds(unique).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, second) -> first));
        return unique.stream()
                .map(found::get)
                .filter(Objects::nonNull)
                .map(UserSummary::from)
                .toList();
    }
}
