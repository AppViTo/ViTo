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
