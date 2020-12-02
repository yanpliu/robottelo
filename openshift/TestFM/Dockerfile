FROM registry.access.redhat.com/ubi8/python-38:latest
MAINTAINER "SatCI"

ENV VIRTUAL_ENV="/opt/app-root" \
    TESTFM_DIR="/opt/app-root/src/testfm" \
    BROKER_DIRECTORY="/opt/app-root/src/testfm" \
    PYCURL_SSL_LIBRARY=openssl \
    ANSIBLE_HOST_KEY_CHECKING="False"

USER 0
RUN yum -y clean all && \
    curl https://password.corp.redhat.com/RH-IT-Root-CA.crt -o /etc/pki/ca-trust/source/anchors/RH-IT-Root-CA.crt && \
    update-ca-trust

USER 1001
WORKDIR "${HOME}"
RUN git clone https://github.com/SatelliteQE/testfm.git && \
    cd testfm && \
    pip install -U pip && \
    pip install -qr requirements.txt && \
    pip install broker && \
    cp testfm/inventory.sample testfm/inventory

COPY broker_settings.yaml ${TESTFM_DIR}/broker_settings.yaml
COPY conf ${TESTFM_DIR}/conf

USER 0
RUN chgrp -R 0 ${VIRTUAL_ENV} && \
    chmod -R g+rwX ${VIRTUAL_ENV} && \
    chmod -R g=u ${VIRTUAL_ENV} /etc/passwd && \
    fix-permissions ${VIRTUAL_ENV} -P

USER 1001
WORKDIR "${TESTFM_DIR}"
