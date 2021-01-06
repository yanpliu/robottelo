FROM registry.access.redhat.com/ubi8/python-38:latest
MAINTAINER "SatCI"

ENV VIRTUAL_ENV="/opt/app-root" \
    HOME="/opt/app-root/src" \
    CLEANER_DIR="/opt/app-root/src/satelliteqe-jenkins-tools/resource_cleanup" \
    PYCURL_SSL_LIBRARY=openssl

# Cloning cloud cleanup script and Installing requirements
USER 1001
WORKDIR "${HOME}"
RUN git clone --depth 1 https://gitlab-ci-token:${CONFIG_FILES_TOKEN}@gitlab.sat.engineering.redhat.com/satelliteqe/satelliteqe-jenkins-tools.git && \
    cd "${CLEANER_DIR}" && \
    pip install -U pip && \
    pip install -qr requirements.txt

# arbitrary UID handling starting from virtualenv directory for pip permissions
USER 0
RUN chgrp -R 0 ${VIRTUAL_ENV} && \
    chmod -R g+rwX ${VIRTUAL_ENV} && \
    fix-permissions ${VIRTUAL_ENV} -P

USER 1001
WORKDIR "${CLEANER_DIR}"
