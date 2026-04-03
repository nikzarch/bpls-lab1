package com.example.labpay.repository.impl;

import com.example.labpay.domain.user.AppUser;
import com.example.labpay.domain.user.Role;
import com.example.labpay.exception.BusinessException;
import com.example.labpay.repository.AppUserRepository;
import com.example.labpay.xml.XmlAppUser;
import com.example.labpay.xml.XmlAppUsers;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class XmlAppUserRepository implements AppUserRepository {

    @Value("${app.security.users-xml-path}")
    private Resource xmlResource;

    @Override
    public Optional<AppUser> findByUsername(String username) {
        return readAll().stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst();
    }

    @Override
    public Optional<AppUser> findById(Long id) {
        return readAll().stream()
                .filter(u -> u.getId().equals(id))
                .findFirst();
    }

    @Override
    public List<AppUser> findAll() {
        return readAll();
    }

    @Override
    public AppUser save(AppUser user) {
        List<AppUser> users = new ArrayList<>(readAll());

        users.removeIf(u -> u.getId().equals(user.getId()) ||
                u.getUsername().equalsIgnoreCase(user.getUsername()));

        if (user.getId() == null) {
            long nextId = users.stream()
                    .map(AppUser::getId)
                    .max(Long::compareTo)
                    .orElse(0L) + 1;
            user.setId(nextId);
        }

        users.add(user);
        writeAll(users);
        return user;
    }

    private List<AppUser> readAll() {
        try (InputStream is = xmlResource.getInputStream()) {
            JAXBContext context = JAXBContext.newInstance(XmlAppUsers.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            XmlAppUsers xmlUsers = (XmlAppUsers) unmarshaller.unmarshal(is);

            return xmlUsers.getUsers().stream()
                    .map(this::mapToDomain)
                    .toList();
        } catch (Exception e) {
            throw new BusinessException("Cannot read users from XML");
        }
    }

    private void writeAll(List<AppUser> users) {
        try {
            JAXBContext context = JAXBContext.newInstance(XmlAppUsers.class);
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            XmlAppUsers xmlUsers = new XmlAppUsers();
            xmlUsers.setUsers(users.stream().map(this::mapToXml).toList());

            File file = xmlResource.getFile();
            marshaller.marshal(xmlUsers, file);
        } catch (Exception e) {
            throw new BusinessException("Cannot write users to XML");
        }
    }

    private AppUser mapToDomain(XmlAppUser xmlUser) {
        return AppUser.builder()
                .id(xmlUser.getId())
                .username(xmlUser.getUsername())
                .passwordHash(xmlUser.getPasswordHash())
                .role(Role.valueOf(xmlUser.getRole()))
                .build();
    }

    private XmlAppUser mapToXml(AppUser user) {
        XmlAppUser xmlUser = new XmlAppUser();
        xmlUser.setId(user.getId());
        xmlUser.setUsername(user.getUsername());
        xmlUser.setPasswordHash(user.getPasswordHash());
        xmlUser.setRole(user.getRole().name());
        return xmlUser;
    }
}