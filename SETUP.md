#Instaructions for Mac, Linux will be similar

Install myxql and configure seucrity
```
brew install mysql
brew services start mysql
mysql_secure_installation
```

Login to test
``
mysql -u root -p
```


I then use `sh loaddata.sh` to load in the samepl data - the source filenames do change sometimes so this might need a little adjustment.

I then download and build  MongoSyphon [https://github.com/johnlpage/MongoSyphon] - this is a tool to convert tables to documents recursively and efficently. The Jar is incluced here for loading or updating in MongoDB if required.

```
java -jar MongoSyphon.jar -c mot_syphon_conf.json 
```




Setting up EC2 Host
------------------
Create an SSH Key on Your Host Machine: Log in to your cloud host or server and generate an SSH keypair. Use the following commands:

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
