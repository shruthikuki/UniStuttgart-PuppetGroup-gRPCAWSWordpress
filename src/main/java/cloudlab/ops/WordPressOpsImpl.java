package cloudlab.ops;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import cloudlab.WordPressOpsProto.BackupReply;
import cloudlab.WordPressOpsProto.BackupRequest;
import cloudlab.WordPressOpsProto.ConnectReply;
import cloudlab.WordPressOpsProto.ConnectRequest;
import cloudlab.WordPressOpsProto.DeployAppReply;
import cloudlab.WordPressOpsProto.DeployAppRequest;
import cloudlab.WordPressOpsProto.DeployDBReply;
import cloudlab.WordPressOpsProto.DeployDBRequest;
import cloudlab.WordPressOpsProto.RestoreReply;
import cloudlab.WordPressOpsProto.RestoreRequest;
import cloudlab.WordPressOpsProto.WordPressOpsGrpc.WordPressOps;
import io.grpc.stub.StreamObserver;

/**
 * This class implements the WordPressOps service interface generated by gRPC
 * using the .proto file defined. Connects to the target VM and issues command
 * via jsch
 */

public class WordPressOpsImpl implements WordPressOps {

	private static final Logger logger = Logger.getLogger(WordPressOpsImpl.class.getName());

	JSch jsch = new JSch();
	String fileSeparator = System.getProperty("file.separator");
	byte[] tmp = new byte[1024];
	AWSCredentials credentials = new ProfileCredentialsProvider().getCredentials();
	AmazonS3 s3 = new AmazonS3Client(credentials);

	@Override
	public void deployApp(DeployAppRequest request, StreamObserver<DeployAppReply> responseObserver) {
		StringBuilder output = new StringBuilder();
		try {
			String object = request.getCredentials() + ".pem";
			S3Object s3Object = s3.getObject(new GetObjectRequest(request.getBucketName(), object));
			InputStream inputStream = s3Object.getObjectContent();

			File outputFile = new File(
					System.getProperty("user.dir") + fileSeparator + request.getCredentials() + ".pem");
			OutputStream outputStream = new FileOutputStream(outputFile);
			int read = 0;
			byte[] bytes = new byte[1024];

			while ((read = inputStream.read(bytes)) != -1) {
				outputStream.write(bytes, 0, read);
			}

			jsch.addIdentity(outputFile.getAbsolutePath());
			jsch.setConfig("StrictHostKeyChecking", "no");

			// Connect to the EC2 instance
			Session session = jsch.getSession(request.getUsername(), request.getPublicIP(), 22);
			session.connect();

			String command = "sudo apt-get -y update && sudo apt-get -y install git && sudo git clone https://github.com/shruthikuki/Puppet-Hunner-Wordpress.git && cd Puppet-Hunner-Wordpress && sudo chmod +x deployApp.sh && sudo ./deployApp.sh";
			Channel channel = session.openChannel("exec");
			((ChannelExec) channel).setCommand(command);
			((ChannelExec) channel).setErrStream(System.err);
			channel.connect();

			InputStream input = channel.getInputStream();

			// start reading the input from the executed commands on the shell
			while (true) {
				while (input.available() > 0) {
					int i = input.read(tmp, 0, 1024);
					if (i < 0)
						break;
					output.append(new String(tmp, 0, i) + "\n");
				}

				if (channel.isClosed()) {
					System.out.println("exit-status: " + channel.getExitStatus());
					break;
				}
				Thread.sleep(1000);
			}

			DeployAppReply reply = DeployAppReply.newBuilder().setOutput(output.toString()).build();
			responseObserver.onNext(reply);
			responseObserver.onCompleted();
			channel.disconnect();
			session.disconnect();
			outputFile.delete();
			outputStream.close();
		} catch (JSchException e) {
			logger.log(Level.WARNING, "JSch failed: {0}", e.getMessage());
		} catch (IOException e) {
			logger.log(Level.WARNING, "IO Exception: {0}", e.getMessage());
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void deployDB(DeployDBRequest request, StreamObserver<DeployDBReply> responseObserver) {
		StringBuilder output = new StringBuilder();
		try {
			String object = request.getCredentials() + ".pem";
			S3Object s3Object = s3.getObject(new GetObjectRequest(request.getBucketName(), object));
			InputStream inputStream = s3Object.getObjectContent();

			File outputFile = new File(
					System.getProperty("user.dir") + fileSeparator + request.getCredentials() + ".pem");
			OutputStream outputStream = new FileOutputStream(outputFile);
			int read = 0;
			byte[] bytes = new byte[1024];

			while ((read = inputStream.read(bytes)) != -1) {
				outputStream.write(bytes, 0, read);
			}

			jsch.addIdentity(outputFile.getAbsolutePath());
			jsch.setConfig("StrictHostKeyChecking", "no");

			// Connect to the EC2 instance
			Session session = jsch.getSession(request.getUsername(), request.getPublicIP(), 22);
			session.connect();

			String command = "cd Puppet-Hunner-Wordpress && sudo chmod +x deployDB.sh && sudo ./deployDB.sh";
			Channel channel = session.openChannel("exec");
			((ChannelExec) channel).setCommand(command);
			((ChannelExec) channel).setErrStream(System.err);
			channel.connect();

			InputStream input = channel.getInputStream();
			// start reading the input from the executed commands on the shell

			while (true) {
				while (input.available() > 0) {
					int i = input.read(tmp, 0, 1024);
					if (i < 0)
						break;
					output.append(new String(tmp, 0, i) + "\n");
				}

				if (channel.isClosed()) {
					System.out.println("exit-status: " + channel.getExitStatus());
					break;
				}
				Thread.sleep(1000);
			}
			DeployDBReply reply = DeployDBReply.newBuilder().setOutput(output.toString()).build();
			responseObserver.onNext(reply);
			responseObserver.onCompleted();
			channel.disconnect();
			session.disconnect();

			outputFile.delete();
			outputStream.close();
		} catch (JSchException e) {
			logger.log(Level.WARNING, "JSch failed: {0}", e.getMessage());
		} catch (IOException e) {
			logger.log(Level.WARNING, "IO Exception: {0}", e.getMessage());
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void connectAppToDB(ConnectRequest request, StreamObserver<ConnectReply> responseObserver) {
		StringBuilder output = new StringBuilder();
		try {
			String object = request.getCredentials() + ".pem";
			S3Object s3Object = s3.getObject(new GetObjectRequest(request.getBucketName(), object));
			InputStream inputStream = s3Object.getObjectContent();

			File outputFile = new File(
					System.getProperty("user.dir") + fileSeparator + request.getCredentials() + ".pem");
			OutputStream outputStream = new FileOutputStream(outputFile);
			int read = 0;
			byte[] bytes = new byte[1024];

			while ((read = inputStream.read(bytes)) != -1) {
				outputStream.write(bytes, 0, read);
			}

			jsch.addIdentity(outputFile.getAbsolutePath());
			jsch.setConfig("StrictHostKeyChecking", "no");

			// Connect to the EC2 instance
			Session session = jsch.getSession(request.getUsername(), request.getPublicIP(), 22);
			session.connect();

			String command = "cd Puppet-Hunner-Wordpress && sudo chmod +x connect.sh && sudo ./connect.sh";
			Channel channel = session.openChannel("exec");
			((ChannelExec) channel).setCommand(command);
			((ChannelExec) channel).setErrStream(System.err);
			channel.connect();

			InputStream input = channel.getInputStream();
			// start reading the input from the executed commands on the shell

			while (true) {
				while (input.available() > 0) {
					int i = input.read(tmp, 0, 1024);
					if (i < 0)
						break;
					output.append(new String(tmp, 0, i) + "\n");
				}

				if (channel.isClosed()) {
					System.out.println("exit-status: " + channel.getExitStatus());
					break;
				}
				Thread.sleep(1000);
			}
			ConnectReply reply = ConnectReply.newBuilder().setOutput(output.toString()).build();
			responseObserver.onNext(reply);
			responseObserver.onCompleted();
			channel.disconnect();
			session.disconnect();

			outputFile.delete();
			outputStream.close();
		} catch (JSchException e) {
			logger.log(Level.WARNING, "JSch failed: {0}", e.getMessage());
		} catch (IOException e) {
			logger.log(Level.WARNING, "IO Exception: {0}", e.getMessage());
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void backupDB(BackupRequest request, StreamObserver<BackupReply> responseObserver) {

	}

	@Override
	public void restoreDB(RestoreRequest request, StreamObserver<RestoreReply> responseObserver) {

	}
}
