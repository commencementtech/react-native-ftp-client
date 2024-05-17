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
declare module FtpClient {
    function setup(config: FtpSetupConfiguration): void;
    function list(remote_path: string): Promise<Array<ListItem>>;
    function uploadFile(local_path: string, remote_path: string): Promise<void>;
    function cancelUploadFile(token: string): Promise<void>;
    function addProgressListener(listener: (data: {
        token: string;
        percentage: number;
    }) => void): EmitterSubscription;
    function remove(remote_path: string): Promise<void>;
    function disconnect(): Promise<void>;
    function systemName(): Promise<void>;
    const ERROR_MESSAGE_CANCELLED: string;
    function downloadFile(local_path: string, remote_path: string): Promise<void>;
    function cancelDownloadFile(token: string): Promise<void>;
}
export default FtpClient;
// rawListing: string,
//     toFormattedString: string,
//     toString: string,