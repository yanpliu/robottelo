FROM registry.access.redhat.com/ubi8/python-38:latest
MAINTAINER "SatCI"

# REQUESTS_CA_BUNDLE is set for ibutsu client's requests use
# BROKER_DIRECTORY is set for robottelo/broker integration
ENV VIRTUAL_ENV="/opt/app-root" \
    HOME="/opt/app-root/src" \
    ROBOTTELO_DIR="/opt/app-root/src/robottelo" \
    BROKER_DIRECTORY="/opt/app-root/src/robottelo" \
    PYCURL_SSL_LIBRARY=openssl \
    REQUESTS_CA_BUNDLE="/etc/pki/tls/certs/ca-bundle.crt"

# need root for yum installs
USER 0
RUN yum -y install https://dl.fedoraproject.org/pub/epel/8/Everything/x86_64/Packages/c/crudini-0.9.3-1.el8.noarch.rpm && \
    yum -y clean all && \
    curl https://password.corp.redhat.com/RH-IT-Root-CA.crt -o /etc/pki/ca-trust/source/anchors/RH-IT-Root-CA.crt && \
    update-ca-trust

# switch to OCP uid for robottelo setup
USER 1001
WORKDIR "${HOME}"
RUN git clone https://github.com/SatelliteQE/robottelo.git && \
    cd robottelo && \
    pip install -U pip && \
    pip install -qr requirements.txt && \
    pip freeze | tee robottelo-pip-freeze.log && \
    git clone --depth 1 https://gitlab-ci-token:${CONFIG_FILES_TOKEN}@gitlab.sat.engineering.redhat.com/satelliteqe/jenkins-configs.git jenkins_configs && \
    cp jenkins_configs/robottelo.properties robottelo.properties && \
    cp jenkins_configs/virtwho.properties virtwho.properties && \
    cp jenkins_configs/robottelo.yaml robottelo.yaml && \
    crudini --del robottelo.properties server ssh_key

# TODO: When we move to latest OCP 4.x, add --chown=1001:0 flag
COPY broker_settings.yaml ${ROBOTTELO_DIR}/broker_settings.yaml
COPY conf ${ROBOTTELO_DIR}/conf

# TODO: Remove the USER root directive when on latest OCP 4.x, although no harm keeping it.
# Switch to user root in order to perform `chown` and `fix-permissions`
# This is required as COPY command above can inadvertently set copied
# file ownership to root:root and cause `chown` and `fix-permissions` to fail
# This only happens on older versions OpenShift, latest Podman handles it well

# arbitrary UID handling starting from virtualenv directory for pip permissions 
USER 0
RUN chgrp -R 0 ${VIRTUAL_ENV} && \
    chmod -R g+rwX ${VIRTUAL_ENV} && \
    fix-permissions ${VIRTUAL_ENV} -P

USER 1001
WORKDIR "${ROBOTTELO_DIR}"
