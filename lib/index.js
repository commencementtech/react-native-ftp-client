import { NativeModules, NativeEventEmitter } from 'react-native';
import { changeDirectory, getDirectory, getSystemDetails } from '../src';

const { RNFtpClient } = NativeModules;
const RNFtpClientEventEmitter = new NativeEventEmitter(RNFtpClient);
export var FtpFileType;
(function(FtpFileType) {
  FtpFileType['Dir'] = 'dir';
  FtpFileType['File'] = 'file';
  FtpFileType['Link'] = 'link';
  FtpFileType['Unknown'] = 'unknown';
})(FtpFileType || (FtpFileType = {}));

var FtpClient;
(function(FtpClient) {
  function getEnumFromString(typeString) {
    switch (typeString) {
      case 'dir':
        return FtpFileType.Dir;
      case 'link':
        return FtpFileType.Link;
      case 'file':
        return FtpFileType.File;
      case 'unknown':
      default:
        return FtpFileType.Unknown;
    }
  }

  function setup(config) {
    RNFtpClient.setup(config.ip_address, config.port, config.username, config.password);
  }
  FtpClient.setup = setup;

  async function login() {
      return RNFtpClient.login();
  }
  FtpClient.login = login;

  async function getSystemDetails() {
      return RNFtpClient.getSystemDetails();
  }
  FtpClient.getSystemDetails = getSystemDetails;

  async function getDirectory(remote_path) {
    const files = await RNFtpClient.getDirectory(remote_path);
    return files?.data.map((f) => {
      return {
        name: f.name,
        type: getEnumFromString(f.type),
        size: +f.size,
        timestamp: new Date(f.timestamp),
        group: f.group,
        hardLinkCount: f.hardLinkCount,
        link: f.link ? f.link : '',
        user: f.user,
        isDirectory: f.isDirectory,
        isFile: f.isFile,
        isSymbolicLink: f.isSymbolicLink,
        isUnknown: f.isUnknown,
        isValid: f.isValid,
        permissions: f.permissions,
        owner: f.owner,
      };
    });
  }
  FtpClient.getDirectory = getDirectory;

  async function changeDirectory(remote_path) {
      return RNFtpClient.changeDirectory(remote_path);
  }
  FtpClient.changeDirectory = changeDirectory;

  async function remove(remote_path) {
      return RNFtpClient.delete(remote_path);
  }
  FtpClient.remove = remove;

  async function upload(local_path, remote_path) {
    return RNFtpClient.upload(local_path, remote_path);
  }
  FtpClient.upload = upload;

  async function cancelUpload(token) {
    return RNFtpClient.cancelUpload(token);
  }
  FtpClient.cancelUpload = cancelUpload;

  async function download(local_path, remote_path) {
      return RNFtpClient.download(local_path, remote_path);
  }
  FtpClient.download = download;






  function addProgressListener(listener) {
    return RNFtpClientEventEmitter.addListener('Progress', listener);
  }

  FtpClient.addProgressListener = addProgressListener;



  async function disconnect() {
    return RNFtpClient.disconnect();
  }

  FtpClient.disconnect = disconnect;



  async function getSystemName() {
    return RNFtpClient.getSystemName();
  }

  FtpClient.systemName = getSystemName;

  async function getfingerprint() {
    return RNFtpClient.getServerFingerprint();
  }

  FtpClient.fingerprint = getfingerprint;

  FtpClient.ERROR_MESSAGE_CANCELLED = RNFtpClient.ERROR_MESSAGE_CANCELLED;



  async function cancelDownloadFile(token) {
    return RNFtpClient.cancelDownloadFile(token);
  }

  FtpClient.cancelDownloadFile = cancelDownloadFile;
})(FtpClient || (FtpClient = {}));
;
export default FtpClient;

//rawListing: f.rawListing,
//toFormattedString: f.toFormattedString,
//toString: f.toString,