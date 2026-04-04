package com.example.labpay.repository.impl;

import com.example.labpay.domain.user.Role;
import com.example.labpay.exception.BusinessException;
import com.example.labpay.repository.AppUserRepository;
import com.example.labpay.xml.XmlAppUser;
import com.example.labpay.xml.XmlAppUsers;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.*;

@Repository
@RequiredArgsConstructor
@Slf4j
public class XmlAppUserRepository implements AppUserRepository {

    @Value("${app.security.users-xml-path}")
    private Resource xmlResource;

    @PostConstruct
    public void validateOnStartup() {
        List<XmlAppUser> users = readAllRaw();
        List<XmlAppUser> valid = new ArrayList<>();
        boolean hadInvalid = false;

        for (XmlAppUser user : users) {
            List<String> errors = validate(user);
            if (errors.isEmpty()) {
                valid.add(user);
            } else {
                hadInvalid = true;
                log.warn("Skipping invalid XML user entry (id={}, username={}): {}",
                        user.getId(), user.getUsername(), String.join("; ", errors));
            }
        }

        if (hadInvalid) {
            log.info("Rewriting users.xml without {} invalid entries", users.size() - valid.size());
            writeAll(valid);
        }

        log.info("XML user store validated: {} valid users loaded", valid.size());
    }

    private List<String> validate(XmlAppUser user) {
        List<String> errors = new ArrayList<>();

        if (user.getId() == null || user.getId() <= 0) {
            errors.add("id is null or non-positive");
        }
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            errors.add("username is null or blank");
        } else if (user.getUsername().length() < 3 || user.getUsername().length() > 50) {
            errors.add("username length must be 3-50");
        }
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            errors.add("passwordHash is null or blank");
        }
        if (user.getRole() == null || user.getRole().isBlank()) {
            errors.add("role is null or blank");
        } else {
            try {
                Role.valueOf(user.getRole());
            } catch (IllegalArgumentException e) {
                errors.add("unknown role: " + user.getRole());
            }
        }

        return errors;
    }

    @Override
    public Optional<XmlAppUser> findByUsername(String username) {
        return readAll().stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst();
    }

    @Override
    public Optional<XmlAppUser> findById(Long id) {
        return readAll().stream()
                .filter(u -> u.getId().equals(id))
                .findFirst();
    }

    @Override
    public List<XmlAppUser> findAll() {
        return readAll();
    }

    @Override
    public XmlAppUser save(XmlAppUser user) {
        List<XmlAppUser> users = new ArrayList<>(readAll());

        users.removeIf(u ->
                (user.getId() != null && user.getId().equals(u.getId())) ||
                        u.getUsername().equalsIgnoreCase(user.getUsername())
        );

        if (user.getId() == null) {
            long nextId = users.stream()
                    .map(XmlAppUser::getId)
                    .max(Long::compareTo)
                    .orElse(0L) + 1;
            user.setId(nextId);
        }

        List<String> errors = validate(user);
        if (!errors.isEmpty()) {
            throw new BusinessException("Invalid user data: " + String.join("; ", errors));
        }

        users.add(user);
        writeAll(users);
        return user;
    }

    private List<XmlAppUser> readAll() {
        List<XmlAppUser> raw = readAllRaw();
        List<XmlAppUser> valid = new ArrayList<>();
        for (XmlAppUser user : raw) {
            if (validate(user).isEmpty()) {
                valid.add(user);
            } else {
                log.warn("Skipping invalid user on read: id={}, username={}", user.getId(), user.getUsername());
            }
        }
        return valid;
    }

    private List<XmlAppUser> readAllRaw() {
        try {
            File file = getOrCreateXmlFile();
            if (file.length() == 0) {
                return new ArrayList<>();
            }

            JAXBContext context = JAXBContext.newInstance(XmlAppUsers.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            XmlAppUsers xmlUsers = (XmlAppUsers) unmarshaller.unmarshal(file);

            return xmlUsers != null && xmlUsers.getUsers() != null
                    ? xmlUsers.getUsers()
                    : new ArrayList<>();
        } catch (Exception e) {
            log.error("Cannot read users from XML, returning empty list", e);
            return new ArrayList<>();
        }
    }

    private void writeAll(List<XmlAppUser> users) {
        try {
            File file = getOrCreateXmlFile();

            JAXBContext context = JAXBContext.newInstance(XmlAppUsers.class);
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            XmlAppUsers xmlUsers = new XmlAppUsers();
            xmlUsers.setUsers(users);

            marshaller.marshal(xmlUsers, file);
        } catch (Exception e) {
            log.error("Cannot write users to XML", e);
            throw new BusinessException("Cannot write users to XML");
        }
    }

    private File getOrCreateXmlFile() {
        try {
            File file = xmlResource.getFile();
            File parent = file.getParentFile();

            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            if (!file.exists()) {
                file.createNewFile();

                JAXBContext context = JAXBContext.newInstance(XmlAppUsers.class);
                Marshaller marshaller = context.createMarshaller();
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

                XmlAppUsers emptyUsers = new XmlAppUsers();
                emptyUsers.setUsers(new ArrayList<>());

                marshaller.marshal(emptyUsers, file);
            }

            return file;
        } catch (Exception e) {
            log.error("Cannot prepare XML file", e);
            throw new BusinessException("Cannot prepare XML file");
        }
    }
}