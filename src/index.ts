import { NativeModules, NativeEventEmitter,EmitterSubscription } from 'react-native';

const { RNFtpClient } = NativeModules;
const RNFtpClientEventEmitter = new NativeEventEmitter(RNFtpClient);

export const enum FtpFileType {
        Dir = "dir",
        File = "file",
        Link = "link",
        Unknown = "unknown",
    };
export interface ListItem{
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
        owner: string
    };

export interface FtpSetupConfiguration{
        ip_address:string,
        port:number,
        username:string,
        password:string
    };

module FtpClient {
    function getEnumFromString(typeString:string):FtpFileType {
        switch (typeString) {
            case "dir":
                return FtpFileType.Dir;    
            case "link":
                return FtpFileType.Link;
            case "file":
                return FtpFileType.File;
            case "unknown":    
            default:
                return FtpFileType.Unknown;
        }
    }

    export function setup (config:FtpSetupConfiguration) {
        RNFtpClient.setup(config.ip_address,config.port,config.username,config.password);
    }
    
    export async function list (remote_path:string):Promise<Array<ListItem>> {
        const files = await RNFtpClient.list(remote_path);
        return files.map((f:{name:string,
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
            owner: string,})=> {
            return {
                name:f.name,
                type:getEnumFromString(f.type),
                size:+f.size,
                timestamp:new Date(f.timestamp),
                group:f.group,
                hardLinkCount:f.hardLinkCount,
                link:f.link,
                user: f.user,
                isDirectory: f.isDirectory,
                isFile:f.isFile,
                isSymbolicLink: f.isSymbolicLink,
                isUnknown: f.isUnknown,
                isValid: f.isValid,
                permissions: f.permissions,
                owner: f.owner
            };});
    }

    export async function uploadFile (local_path:string,remote_path:string):Promise<void> {
        return RNFtpClient.uploadFile(local_path,remote_path);
    }

    export async function cancelUploadFile (token:string):Promise<void> {
        return RNFtpClient.cancelUploadFile(token);
    }

    export function addProgressListener(listener: ( data:{token:string, percentage:number}) => void):EmitterSubscription  {
        return RNFtpClientEventEmitter.addListener("Progress",listener);
    }

    export async function remove(remote_path:string):Promise<void>{
        return RNFtpClient.remove(remote_path);
    }

    export const ERROR_MESSAGE_CANCELLED:string = RNFtpClient.ERROR_MESSAGE_CANCELLED;

    export async function downloadFile (local_path:string,remote_path:string):Promise<void> {
        return RNFtpClient.downloadFile(local_path,remote_path);
    }

    export async function cancelDownloadFile (token:string):Promise<void> {
        return RNFtpClient.cancelDownloadFile(token);
    }
};

export default FtpClient;
// rawListing: f.rawListing,
//     toFormattedString: f.toFormattedString,
//     toString: f.toString,

// rawListing: string,
//     toFormattedString: string,
//     toString: string,