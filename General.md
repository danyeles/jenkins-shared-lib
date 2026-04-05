¡Perfecto, Daniel!Aquí tienes TODOS los pasos, 100% en Markdown, sin texto fuera del bloque, listos para copiar/pegar directamente como README.md.

# Jenkins Local Infra + GitHub SSH + ArrSeed + ARR Suite Deployment

Este documento describe el proceso completo para:

- Instalar Jenkins localmente  
- Configurar autenticación SSH con GitHub  
- Registrar credenciales en Jenkins  
- Ejecutar el Seed Job (`seed.groovy`)  
- Generar automáticamente los pipelines de instalación  
- Instalar la suite completa de aplicaciones *arr* (Sonarr, Radarr, Lidarr, etc.)

Todo está organizado como un playbook reproducible.

---

# 1. Instalación de Jenkins

## Instalar Java
```bash
sudo apt update
sudo apt install -y openjdk-17-jdk
java -version
```

Instalar Jenkins
```
curl -fsSL https://pkg.jenkins.io/debian-stable/jenkins.io.key | sudo tee \
  /usr/share/keyrings/jenkins-keyring.asc > /dev/null

echo deb [signed-by=/usr/share/keyrings/jenkins-keyring.asc] \
  https://pkg.jenkins.io/debian-stable binary/ | sudo tee \
  /etc/apt/sources.list.d/jenkins.list > /dev/null

sudo apt update
sudo apt install -y jenkins
```

Iniciar Jenkins
```
sudo systemctl enable jenkins
sudo systemctl start jenkins
sudo systemctl status jenkins
```

2. Configurar SSH local para GitHub

Verificar claves existentes
```
ls -al ~/.ssh
```
Crear clave SSH (si no existe)
```
ssh-keygen -t ed25519 -C "tu_correo_de_github"
```
Activar ssh-agent
```
eval "$(ssh-agent -s)"
ssh-add ~/.ssh/id_ed25519
```
Copiar clave pública
```
cat ~/.ssh/id_ed25519.pub
```
Agregar en GitHub:Settings → SSH and GPG Keys → New SSH Key

3. Configurar Jenkins para usar tu clave SSH

Crear directorio .ssh del usuario Jenkins
```
sudo rm -f /var/lib/jenkins/.ssh
sudo mkdir -p /var/lib/jenkins/.ssh
sudo chmod 700 /var/lib/jenkins/.ssh
sudo chown jenkins:jenkins /var/lib/jenkins/.ssh
```
Copiar tu clave privada y pública
```
sudo cp ~/.ssh/id_ed25519 /var/lib/jenkins/.ssh/
sudo cp ~/.ssh/id_ed25519.pub /var/lib/jenkins/.ssh/

sudo chown jenkins:jenkins /var/lib/jenkins/.ssh/id_ed25519*
sudo chmod 600 /var/lib/jenkins/.ssh/id_ed25519
sudo chmod 644 /var/lib/jenkins/.ssh/id_ed25519.pub
```
Crear archivo config
```
sudo bash -c 'cat > /var/lib/jenkins/.ssh/config <<EOF
Host github.com
    HostName github.com
    User git
    IdentityFile /var/lib/jenkins/.ssh/id_ed25519
    IdentitiesOnly yes
EOF'

sudo chown jenkins:jenkins /var/lib/jenkins/.ssh/config
sudo chmod 600 /var/lib/jenkins/.ssh/config
```
4. Agregar host key de GitHub (Strict Checking)

Entrar como usuario Jenkins
```
sudo su - jenkins
```
Crear known_hosts
```
mkdir -p ~/.ssh
touch ~/.ssh/known_hosts
chmod 600 ~/.ssh/known_hosts
```
Agregar host key
```
ssh-keyscan github.com >> ~/.ssh/known_hosts
```
Validar conexión
```
ssh -T git@github.com
```
Salir:
```
exit
```
5. Crear credencial SSH en Jenkins

En Jenkins UI:

Manage Jenkins

Credentials

System

Global credentials

Add Credentials

Valores:

Kind: SSH Username with private key

Username: git

Private Key: Enter directly

Pegar contenido de:
```
sudo su - jenkins
cat ~/.ssh/id_ed25519
```
ID: github-ssh

Guardar.

6. Crear Pipeline clásico para ejecutar seed.groovy

Este paso reemplaza completamente al Multibranch Pipeline.

Crear Pipeline

En Jenkins:

New Item → Pipeline
Name: ArrSeed

Configurar Pipeline script from SCM

Definition: Pipeline script from SCM

SCM: Git

Repository URL:

git@github.com:danyeles/jenkins-infra.git

Credentials:

github-ssh

Script Path:

seed.groovy

Guardar.

Ejecutar ArrSeed

Build Now

Este job:

Ejecuta DSL

Crea carpetas

Crea todos los pipelines de instalación

Prepara la estructura de la suite de arr apps

7. Validar acceso a repos desde Jenkins
```
sudo su - jenkins
git clone git@github.com:danyeles/jenkins-infra.git
git clone git@github.com:danyeles/jenkins-shared-lib.git
exit
```
8. Registrar jenkins-shared-lib como Global Shared Library

En Jenkins:

Manage Jenkins

Configure System

Global Pipeline Libraries

Add Library

Valores:

Name: jenkins-shared-lib

Default version: main

SCM: Git

Repository URL:

git@github.com:danyeles/jenkins-shared-lib.git

Credentials: github-ssh

Guardar.

9. Ejecutar los jobs generados por ArrSeed

Después de correr ArrSeed, Jenkins crea jobs como:

Install-Sonarr

Install-Radarr

Install-Lidarr

Install-Prowlarr

Install-Readarr

Install-Bazarr

Install-Qbittorrent

Install-Jellyfin

etc.

Para cada uno:

Entrar al job

Clic en Build Now

Cada job:

Instala la app

Configura rutas

Crea servicios

Valida health checks

10. Validación final

Verificar servicios

sudo systemctl status sonarr
sudo systemctl status radarr
sudo systemctl status prowlarr
sudo systemctl status qbittorrent
sudo systemctl status jellyfin

Verificar puertos

sudo netstat -tulpn | grep -E "7878|8989|9696|8096|8080"

✔️ Todo listo

Tu laptop ahora funciona como un entorno completo de despliegue:

Jenkins instalado

SSH configurado

Credenciales funcionando

Shared library registrada

Seed job ejecutado

Pipelines generados

Suite de arr apps instalada vía Jenkins


---

Si quieres, puedo ayudarte a generar también un **bootstrap.sh**, un **playbook de Ansible**, o un **diagrama arquitectónico** para documentarlo aún mejor.
