FROM registry.access.redhat.com/ubi8/python-38:latest
MAINTAINER "SatCI"

# REQUESTS_CA_BUNDLE is set for ibutsu client's requests use
# ROBOTTELO_DIR environment variable is needed by dynaconf's robottelo.yaml file
ENV VIRTUAL_ENV="/opt/app-root" \
    HOME="/opt/app-root/src" \
    ROBOTTELO_DIR="/opt/app-root/src/robottelo" \
    UPGRADE_DIR="/opt/app-root/src/satellite6-upgrade" \
    BROKER_DIRECTORY="/opt/app-root/src/satellite6-upgrade" \
    PYCURL_SSL_LIBRARY=openssl \
    REQUESTS_CA_BUNDLE="/etc/pki/tls/certs/ca-bundle.crt"

# need root for yum installs
USER 0
RUN yum -y install iputils nmap-ncat && \
    yum -y clean all && \
    curl https://password.corp.redhat.com/RH-IT-Root-CA.crt -o /etc/pki/ca-trust/source/anchors/RH-IT-Root-CA.crt && \
    update-ca-trust

# switch to OCP uid for upgrade setup
USER 1001
WORKDIR "${HOME}"

RUN git clone https://github.com/SatelliteQE/satellite6-upgrade.git && \
    cd satellite6-upgrade && \
    pip install -U pip && \
    pip install -qr requirements.txt

# TODO: When we move to latest OCP 4.x, add --chown=1001:0 flag
COPY broker_settings.yaml ${UPGRADE_DIR}/broker_settings.yaml
COPY conf ${UPGRADE_DIR}/conf

# TODO: Remove the USER root directive when on latest OCP 4.x, although no harm keeping it.
# Switch to user root in order to perform `chown` and `fix-permissions`
# This is required as COPY command above can inadvertently set copied
# file ownership to root:root and cause `chown` and `fix-permissions` to fail
# This only happens on older versions OpenShift, latest Podman handles it well

# arbitrary UID handling starting from virtualenv directory for pip permissions
USER 0
RUN chgrp -R 0 ${VIRTUAL_ENV} && \
    chmod -R g+rwX ${VIRTUAL_ENV} && \
    chmod -R g=u ${VIRTUAL_ENV} /etc/passwd && \
    fix-permissions ${VIRTUAL_ENV} -P

USER 1001
WORKDIR "${UPGRADE_DIR}"
