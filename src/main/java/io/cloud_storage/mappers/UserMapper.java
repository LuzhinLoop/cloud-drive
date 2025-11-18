package io.cloud_storage.mappers;

import io.cloud_storage.domain.dto.UserDto;
import io.cloud_storage.domain.model.User;
import io.cloud_storage.domain.request.UserRequestDto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    User toEntity(UserRequestDto request);
}
