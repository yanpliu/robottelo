FROM registry.access.redhat.com/ubi8/python-36:latest
MAINTAINER "SatCI"

ENV HOME="/opt/app-root/src/broker"

# Install broker
RUN git clone --branch update-timeout --single-branch  https://github.com/mshriver/broker.git && \
    cd broker && \
    pip install . && \
    cp broker_settings.yaml.example broker_settings.yaml

# TODO: Pull settings file from source (gitlab or AT). Store in same directory as Dockerfile

ENV BROKER_DIRECTORY="$HOME"
WORKDIR "$HOME"

RUN chown -R 1001:0 ${HOME} && \
    fix-permissions ${HOME} -P

USER 1001
