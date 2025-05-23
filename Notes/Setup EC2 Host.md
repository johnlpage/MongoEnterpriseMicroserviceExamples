Setting up EC2 Host to Test with Atlas
------------------

sudo yum install -y git

Create an SSH Key on Your Host Machine: Log in to your cloud host or server and generate an SSH keypair. Use the
following commands:

# Generate an SSH key (do not set a passphrase)

ssh-keygen -t rsa -b 4096 -C "your_email@example.com"

You'll be prompted to provide a filename for the key (e.g., /home/your_user/.ssh/github-readonly-key).

This will generate:

A private key (e.g., github-readonly-key)
A public key (e.g., github-readonly-key.pub)
Copy the Public Key: Retrieve the contents of the public key:

cat /home/your_user/.ssh/github-readonly-key.pub

Copy the output string to your clipboard.

Add the Deploy Key to Your GitHub Repository:

Go to your private repository on GitHub.
Navigate to Settings > Deploy keys > Add deploy key.
Paste the content of your public key.
Make sure to check the Allow read access box (it is read-only by default unless checked for write access).
Save the Deploy Key.
Test the SSH Key on Your Cloud Host: Add the private key to your SSH agent on the cloud host:

eval "$(ssh-agent -s)"
ssh-add /home/your_user/.ssh/github-readonly-key

Test the connection:

ssh -T git@github.com

You should see a success message indicating that GitHub has been accessed.

Clone or Pull the Repository: Use the SSH URL to clone or update the repository on your cloud host:

git clone git@github.com:your-username/your-repo.git

sudo dnf update -y

sudo dnf install -y java-23-amazon-corretto

sudo dnf install -y maven

wget https://archive.apache.org/dist/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz
sudo tar -xzf apache-maven-3.6.3-bin.tar.gz -C /opt

sudo ln -s /opt/apache-maven-3.6.3 /opt/maven
sudo nano /etc/profile.d/maven.sh

Set Up Environment Variables:

Create or edit a profile script to set up Maven environment variables globally. For instance, you can create a file
named maven.sh in /etc/profile.d/:

sudo nano /etc/profile.d/maven.sh

Add the following lines to the file:

export M2_HOME=/opt/maven
export PATH=${M2_HOME}/bin:${PATH}

