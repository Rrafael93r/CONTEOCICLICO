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

// Production API Configuration
const apiBaseUrl = config.get("apiBaseUrl") || "https://apiciclico.pharmaser.com.co";
const apiKey = config.get("apiKey") || "pharmaser_secure_api_key_2026";

const authToken = config.requireSecret("authToken");
const certificateId = config.requireSecret("certificateId");

// Database and SFTP Secrets (Should be moved to Pulumi secrets)
const dbUrl = config.get("dbUrl") || "jdbc:mysql://10.0.1.115:3306/conteociclico?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=America/Bogota&zeroDateTimeBehavior=convertToNull&rewriteBatchedStatements=true&cachePrepStmts=true&prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048&useServerPrepStmts=true";
const dbUser = config.get("dbUser") || "admin";
const dbPassword = config.getSecret("dbPassword") || pulumi.secret("Adm1n*.2024/");
const jwtSecret = config.getSecret("jwtSecret") || pulumi.secret("super_secret_production_key_change_me_123456789");

const ad = oci.identity.getAvailabilityDomainOutput({
    compartmentId: compartmentId,
    adNumber: 1,
});

// Backend
const backendImage = new docker.Image("conteo_api-image", {
    build: {
        context: "../BACK",
        dockerfile: "../BACK/Dockerfile",
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
            APP_AUTO_IMPORT_ENABLED: "true",
            APP_AUTO_IMPORT_DELETE_AFTER_SUCCESS: "true",
            DB_URL: dbUrl,
            DB_USER: dbUser,
            DB_PASSWORD: dbPassword,
            API_KEY: apiKey,
            JWT_SECRET: jwtSecret,
            SFTP_HOST: config.get("sftpHost") || "ftpharmaser.pharmaser.com.co",
            SFTP_USER: config.get("sftpUser") || "cporto",
            SFTP_PASSWORD: config.getSecret("sftpPassword") || pulumi.secret("Ph@rm4s3r."),
            SFTP_PORT: config.get("sftpPort") || "22",
            SFTP_REMOTE_PATH: config.get("sftpRemotePath") || "/medicar/ciclicoinventario"
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
const frontendImage = new docker.Image("conteo_ui-image", {
    build: {
        context: "../FRONT",
        dockerfile: "../FRONT/Dockerfile",
        platform: "linux/amd64",
        args: {
            VITE_API_BASE_URL: apiBaseUrl,
            VITE_API_KEY: apiKey,
        },
    },
    imageName: pulumi.interpolate`${region}.ocir.io/${namespace}/${repoFront}:latest`,
    registry: {
        server: pulumi.interpolate`${region}.ocir.io`,
        username: pulumi.interpolate`${namespace}/develop@pharmaser.com.co`,
        password: authToken,
    },
});

const frontendContainerInstance = new oci.containerengine.ContainerInstance("conteo_ui-instance", {
    compartmentId: compartmentId,
    displayName: "conteo_ui-instance",
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
        displayName: "conteo_ui-container",
        environmentVariables: {
            VITE_API_BASE_URL: apiBaseUrl,
            VITE_API_KEY: apiKey,
        },
    }],
});

const frontendLb = new oci.loadbalancer.LoadBalancer("conteo_ui-lb", {
    compartmentId: compartmentId,
    displayName: "conteo_ui-lb",
    shape: "flexible",
    subnetIds: [publicSubnetId],
    shapeDetails: {
        maximumBandwidthInMbps: 100,
        minimumBandwidthInMbps: 10
    },
});

const frontendBackendset = new oci.loadbalancer.BackendSet("conteo_ui-backend-set", {
    loadBalancerId: frontendLb.id,
    name: "conteo_ui-backend-set",
    policy: "ROUND_ROBIN",
    healthChecker: {
        protocol: "HTTP",
        urlPath: "/",
        port: 80,
        returnCode: 200,
    }
});

const frontendListener = new oci.loadbalancer.Listener("conteo_ui-listener", {
    loadBalancerId: frontendLb.id,
    name: "conteo_ui-listener",
    defaultBackendSetName: frontendBackendset.name,
    port: 443,
    protocol: "HTTP",
    sslConfiguration: {
        certificateIds: [certificateId],
        verifyPeerCertificate: false,
    }
});

const frontendBackend = new oci.loadbalancer.Backend("conteo_ui-backend", {
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