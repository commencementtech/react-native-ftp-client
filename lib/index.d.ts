import { EmitterSubscription } from 'react-native';
export declare const enum FtpFileType {
    Dir = "dir",
    File = "file",
    Link = "link",
    Unknown = "unknown"
}
export interface ListItem {
    name:string,
    type: FtpFileType,
    size:number,
    timestamp:Date,
    group: string,
    hardLinkCount: number,
    link: string,
    user: string,
    isDirectory: boolean,
    isFile: boolean,
    isSymbolicLink: boolean,
    isUnknown: boolean,
    isValid: boolean,
    permissions: string,
    owner: string,
}
export interface FtpSetupConfiguration {
    ip_address: string;
    port: number;
    username: string;
    password: string;
}
export interface systemDetails {
    systemType:string;
    status:string;
    replyString:string;
    controlEncoding:string;
    reply:number;
    replyCode:number;
    bufferSize:number;
    localPort:number;
    passivePort:number;
    dataConnectionMode:number;
    defaultPort:number;
    receiveDataSocketBufferSize:number;
    sendDataSocketBufferSize:number;
    enableSessionCreation:boolean;
    hostnameVerifier:string;
    remotePort:number;
    systemName:string;
    hostAddress:string;
    hostName:string;
    canonicalHostName:string;
    address:string;
    passiveHost:string;
    passiveHostAddress:string;
    localAddress:string;
}

declare module FtpClient {
    function setup(config: FtpSetupConfiguration): void;
    function login(): Promise<void>;
    function getSystemDetails(): Promise<systemDetails>;
    function getDirectory(remote_path: string): Promise<Array<ListItem>>;
    function changeDirectory(remote_path: string): Promise<void>;
    function remove(remote_path: string): Promise<void>;
    function upload(local_path: string, remote_path: string): Promise<void>;
    function cancelUpload(token: string): Promise<void>;
    function download(local_path: string, remote_path: string): Promise<void>;


    function addProgressListener(listener: (data: {
        token: string;
        percentage: number;
    }) => void): EmitterSubscription;

    function disconnect(): Promise<void>;
    function systemName(): Promise<void>;
    function fingerprint(): Promise<void>;
    const ERROR_MESSAGE_CANCELLED: string;
    function cancelDownloadFile(token: string): Promise<void>;
}
export default FtpClient;
// rawListing: string,
//     toFormattedString: string,
//     toString: string,