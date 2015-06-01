# -*- encoding: utf-8 -*-
"""Tests for the ``hostgroups`` paths."""
from fauxfactory import gen_string
from nailgun import client, entities, entity_fields
from robottelo.common.constants import (
    FAKE_0_PUPPET_REPO,
    PUPPET_MODULE_NTP_PUPPETLABS,
)
from robottelo.common.decorators import stubbed
from robottelo.common.helpers import get_data_file, get_server_credentials
from robottelo.test import APITestCase
# (too-many-public-methods) pylint:disable=R0904


class HostGroupTestCase(APITestCase):
    """Tests for host group entity."""

    def test_bz_1107708(self):
        """@Test: Host that created from HostGroup entity with PuppetClass
        assigned to it should inherit such puppet class information under
        'all_puppetclasses' field

        @Feature: HostGroup and Host

        @Assert: Host inherited 'all_puppetclasses' details from HostGroup that
        was used for such Host create procedure

        """
        # Creating entities like organization, content view and lifecycle_env
        # with not utf-8 names for easier interaction with puppet environment
        # further in test
        org = entities.Organization(name=gen_string('alpha')).create()
        location = entities.Location(organization=[org]).create()
        # Creating puppet repository with puppet module assigned to it
        product = entities.Product(organization=org).create()
        puppet_repo = entities.Repository(
            content_type='puppet',
            product=product,
            url=FAKE_0_PUPPET_REPO,
        ).create()
        puppet_repo.sync()
        with open(get_data_file(PUPPET_MODULE_NTP_PUPPETLABS), 'rb') as handle:
            puppet_repo.upload_content(handle)

        content_view = entities.ContentView(
            organization=org,
            name=gen_string('alpha'),
        ).create()
        results = content_view.available_puppet_modules()['results']
        # Working with 'ntp' module as we know for sure that it contains at
        # least few puppet classes
        for result in results:
            if result['name'] == 'ntp':
                content_view.add_puppet_module(
                    result['author'],
                    result['name']
                )
                break

        content_view.publish()
        cvv = entities.ContentViewVersion(
            id=content_view.read_json()['versions'][0]['id']
        )
        lc_env = entities.LifecycleEnvironment(
            organization=org,
            name=gen_string('alpha')
        ).create()
        cvv.promote(lc_env.id)
        cv_attrs = content_view.read_json()
        self.assertEqual(len(cv_attrs['versions']), 1)
        self.assertEqual(len(cv_attrs['puppet_modules']), 1)

        # Form environment name variable for our test
        env_name = 'KT_{0}_{1}_{2}_{3}'.format(
            org.name, lc_env.name, content_view.name, str(content_view.id))

        # Get all environments for current organization.
        # We have two environments (one created after publishing and one more
        # was created after promotion), so we need to select promoted one
        response = client.get(
            entities.Environment().path(),
            auth=get_server_credentials(),
            data={
                u'organization_id': org.id,
            },
            verify=False,
        )
        response.raise_for_status()
        results = response.json()['results']
        self.assertEqual(len(results), 2)
        env_id = None
        for result in results:
            if result['name'] == env_name:
                env_id = result['id']
                break
        environment = entities.Environment(
            id=env_id
        ).read()

        # Prepare necessary data for HostGroup entity
        mac = entity_fields.MACAddressField().gen_value()
        root_pass = entity_fields.StringField(length=(8, 30)).gen_value()
        domain = entities.Domain().create()
        architecture = entities.Architecture().create()
        ptable = entities.PartitionTable().create()
        operatingsystem = entities.OperatingSystem(
            architecture=[architecture],
            ptable=[ptable],
        ).create()
        medium = entities.Media(
            operatingsystem=[operatingsystem]
        ).create()

        host_group = entities.HostGroup(
            name=gen_string('alpha'),
            architecture=architecture,
            environment=environment,
            operatingsystem=operatingsystem,
            ptable=ptable,
            domain=domain,
            medium=medium,
            location=[location.id],
            organization=[org.id],
        ).create()

        self.assertEqual(len(host_group.read_json()['all_puppetclasses']), 0)

        # Get puppet class id for ntp module
        response = client.get(
            entities.Environment(id=env_id).path()+'/puppetclasses',
            auth=get_server_credentials(),
            verify=False,
        )
        response.raise_for_status()
        results = response.json()['results']
        puppet_class_id = results['ntp'][0]['id']

        # Assign puppet class
        client.post(
            entities.HostGroup(id=host_group.id).path()+'/puppetclass_ids',
            data={u'puppetclass_id': puppet_class_id},
            auth=get_server_credentials(),
            verify=False
        ).raise_for_status()
        self.assertEqual(len(host_group.read_json()['all_puppetclasses']), 1)
        self.assertEqual(
            host_group.read_json()['all_puppetclasses'][0]['name'],
            'ntp'
        )

        # Create Host entity using HostGroup
        host = entities.Host(
            hostgroup=host_group,
            mac=mac,
            root_pass=root_pass,
            environment=environment,
            location=location,
            organization=org,
            name=gen_string('alpha')
        ).create(False)

        self.assertEqual(len(host.read_json()['all_puppetclasses']), 1)
        self.assertEqual(
            host.read_json()['all_puppetclasses'][0]['name'],
            'ntp'
        )


class HostGroupTestCaseStub(APITestCase):
    """Incomplete tests for host groups.

    When implemented, each of these tests should probably be data-driven. A
    decorator of this form might be used::

        @data(
            name is alpha,
            name is alpha_numeric,
            name is html,
            name is latin1,
            name is numeric,
            name is utf-8,
        )

    """

    @stubbed()
    def test_remove_hostgroup_1(self, test_data):
        """
        @feature: Organizations
        @test: Add a hostgroup and remove it by using the organization
        name and hostgroup name
        @assert: hostgroup is added to organization then removed
        @status: manual
        """

    @stubbed()
    def test_remove_hostgroup_2(self, test_data):
        """
        @feature: Organizations
        @test: Add a hostgroup and remove it by using the organization
        ID and hostgroup name
        @assert: hostgroup is added to organization then removed
        @status: manual
        """

    @stubbed()
    def test_remove_hostgroup_3(self, test_data):
        """
        @feature: Organizations
        @test: Add a hostgroup and remove it by using the organization
        name and hostgroup ID
        @assert: hostgroup is added to organization then removed
        @status: manual
        """

    @stubbed()
    def test_remove_hostgroup_4(self, test_data):
        """
        @feature: Organizations
        @test: Add a hostgroup and remove it by using the organization
        ID and hostgroup ID
        @assert: hostgroup is added to organization then removed
        @status: manual
        """

    @stubbed()
    def test_add_hostgroup_1(self, test_data):
        """
        @feature: Organizations
        @test: Add a hostgroup by using the organization
        name and hostgroup name
        @assert: hostgroup is added to organization
        @status: manual
        """

    @stubbed()
    def test_add_hostgroup_2(self, test_data):
        """
        @feature: Organizations
        @test: Add a hostgroup by using the organization
        ID and hostgroup name
        @assert: hostgroup is added to organization
        @status: manual
        """

    @stubbed()
    def test_add_hostgroup_3(self, test_data):
        """
        @feature: Organizations
        @test: Add a hostgroup by using the organization
        name and hostgroup ID
        @assert: hostgroup is added to organization
        @status: manual
        """

    @stubbed()
    def test_add_hostgroup_4(self, test_data):
        """
        @feature: Organizations
        @test: Add a hostgroup by using the organization
        ID and hostgroup ID
        @assert: hostgroup is added to organization
        @status: manual
        """
