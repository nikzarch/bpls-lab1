package com.example.labpay.repository.impl;

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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Slf4j
public class XmlAppUserRepository implements AppUserRepository {

    @Value("${app.security.users-xml-path}")
    private Resource xmlResource;

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

        users.add(user);
        writeAll(users);
        return user;
    }

    private List<XmlAppUser> readAll() {
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
            log.error("Cannot read users from XML", e);
            throw new BusinessException("Cannot read users from XML");
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