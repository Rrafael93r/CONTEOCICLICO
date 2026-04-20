import * as pulumi from "@pulumi/pulumi";
import * as oci from "@pulumi/oci";
import * as docker from "@pulumi/docker";

const config = new pulumi.Config();
const compartmentId = config.require("compartmentId");
const privateSubnetId = config.require("privateSubnetId");
const publicSubnetId = config.require("publicSubnetId");
const region = config.require("region");
const namespace = config.require("namespace");

const repoBack = config.require("repoBack");
const repoFront = config.require("repoFront");

const authToken = config.requireSecret("authToken");
const certificateId = config.requireSecret("certificateId");

const ad = oci.identity.getAvailabilityDomainOutput({
    compartmentId: compartmentId,
    adNumber: 1,
});

// Backend
const backendImage = new docker.Image("conteo_api-image", {
    build: {
        context: "../code/BACK",
        dockerfile: "../code/BACK/Dockerfile",
        platform: "linux/amd64",
    },
    imageName: pulumi.interpolate`${region}.ocir.io/${namespace}/${repoBack}:latest`,
    registry: {
        server: pulumi.interpolate`${region}.ocir.io`,
        username: pulumi.interpolate`${namespace}/develop@pharmaser.com.co`,
        password: authToken,
    },
});

const backendContainerInstance = new oci.containerengine.ContainerInstance("conteo_api-instance", {
    compartmentId: compartmentId,
    displayName: "conteo_api-instance",
    availabilityDomain: ad.name,
    shape: "CI.Standard.E4.Flex",
    shapeConfig: {
        ocpus: 1,
        memoryInGbs: 2,
    },
    vnics: [{
        subnetId: privateSubnetId,
        isPublicIpAssigned: false,
    }],
    containers: [{
        imageUrl: backendImage.imageName,
        displayName: "conteo_api-container",
        environmentVariables: {
            APP_API_KEY: "PHARMASER_ZAFIRO_RROJAS_CPORTO",
            VALIDAR_PASSWORD: "Ph4rM453rPr3Vr3N41",
            JWT_SECRET: "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
        },
    }],
});

const backendLb = new oci.loadbalancer.LoadBalancer("conteo_api-lb", {
    compartmentId: compartmentId,
    displayName: "conteo_api-lb",
    shape: "flexible",
    subnetIds: [publicSubnetId],
    shapeDetails: {
        maximumBandwidthInMbps: 100,
        minimumBandwidthInMbps: 10
    },
});

const backendBackendset = new oci.loadbalancer.BackendSet("conteo_api-backend-set", {
    loadBalancerId: backendLb.id,
    name: "conteo_api-backend-set",
    policy: "ROUND_ROBIN",
    healthChecker: {
        protocol: "HTTP",
        urlPath: "/actuator/health",
        port: 8080,
        returnCode: 200,
    }
});

const backendListener = new oci.loadbalancer.Listener("conteo_api-listener", {
    loadBalancerId: backendLb.id,
    name: "conteo_api-listener",
    defaultBackendSetName: backendBackendset.name,
    port: 443,
    protocol: "HTTP",
    sslConfiguration: {
        certificateIds: [certificateId],
        verifyPeerCertificate: false,
    }
});

const backendBackend = new oci.loadbalancer.Backend("conteo_api-backend", {
    loadBalancerId: backendLb.id,
    backendsetName: backendBackendset.name,
    ipAddress: backendContainerInstance.vnics.apply(vnics => vnics[0].privateIp),
    port: 8080,
    backup: false,
    drain: false,
    offline: false,
    weight: 1,
});

// Frontend
const frontendImage = new docker.Image("zafiro-ui-image", {
    build: {
        context: "../code/zafiro.ui",
        dockerfile: "../code/zafiro.ui/Dockerfile",
        platform: "linux/amd64",
    },
    imageName: pulumi.interpolate`${region}.ocir.io/${namespace}/${repoFront}:latest`,
    registry: {
        server: pulumi.interpolate`${region}.ocir.io`,
        username: pulumi.interpolate`${namespace}/develop@pharmaser.com.co`,
        password: authToken,
    },
});

const frontendContainerInstance = new oci.containerengine.ContainerInstance("zafiro-ui-instance", {
    compartmentId: compartmentId,
    displayName: "zafiro-ui-instance",
    availabilityDomain: ad.name,
    shape: "CI.Standard.E4.Flex",
    shapeConfig: {
        ocpus: 1,
        memoryInGbs: 2,
    },
    vnics: [{
        subnetId: privateSubnetId,
        isPublicIpAssigned: false,
    }],
    containers: [{
        imageUrl: frontendImage.imageName,
        displayName: "zafiro-ui-container",
        environmentVariables: {
            VITE_API_URL: "https://apizafiro.pharmaser.com.co",
            VITE_API_KEY: "PHARMASER_ZAFIRO_RROJAS_CPORTO",
            VITE_PROXY_URL: "https://apizafiro.pharmaser.com.co/proxy",
            VITE_API_SEDES: "/sedes",
            VITE_API_TURNOS: "/turnos",
            VITE_API_VALIDARDOCUMENTO: "/validar-documento",
            VITE_API_DISPONIBILIDAD: "/disponibilidad",
        },
    }],
});

const frontendLb = new oci.loadbalancer.LoadBalancer("zafiro-ui-lb", {
    compartmentId: compartmentId,
    displayName: "zafiro-ui-lb",
    shape: "flexible",
    subnetIds: [publicSubnetId],
    shapeDetails: {
        maximumBandwidthInMbps: 100,
        minimumBandwidthInMbps: 10
    },
});

const frontendBackendset = new oci.loadbalancer.BackendSet("zafiro-ui-backend-set", {
    loadBalancerId: frontendLb.id,
    name: "zafiro-ui-backend-set",
    policy: "ROUND_ROBIN",
    healthChecker: {
        protocol: "HTTP",
        urlPath: "/",
        port: 80,
        returnCode: 200,
    }
});

const frontendListener = new oci.loadbalancer.Listener("zafiro-ui-listener", {
    loadBalancerId: frontendLb.id,
    name: "zafiro-ui-listener",
    defaultBackendSetName: frontendBackendset.name,
    port: 443,
    protocol: "HTTP",
    sslConfiguration: {
        certificateIds: [certificateId],
        verifyPeerCertificate: false,
    }
});

const frontendBackend = new oci.loadbalancer.Backend("zafiro-ui-backend", {
    loadBalancerId: frontendLb.id,
    backendsetName: frontendBackendset.name,
    ipAddress: frontendContainerInstance.vnics.apply(vnics => vnics[0].privateIp),
    port: 80,
    backup: false,
    drain: false,
    offline: false,
    weight: 1,
});



export const frontendLbPublicIp = frontendLb.ipAddresses;
export const backendLbPublicIp = backendLb.ipAddresses;