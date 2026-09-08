# CargoPlus

Sistema de **cargos, hierarquia e permissões** para servidores Minecraft, integrado ao ecossistema do servidor e ao LoginPlus.

## ✨ Funcionalidades

### 👑 Sistema de cargos
- Cargos configuráveis em hierarquia.
- Promoção para o próximo cargo disponível.
- Definição manual de cargo.
- Remoção do cargo personalizado e retorno ao cargo padrão.
- Aplicação das permissões do cargo ao jogador.
- Persistência dos dados dos jogadores.

### 🔐 Permissões
O CargoPlus centraliza as permissões dos cargos e permite controlar quem pode administrar a hierarquia.

Permissões administrativas principais:
- `cargoplus.promover`
- `cargoplus.setcargo`
- `cargoplus.removercargo`
- `cargoplus.admin`

### 🎨 Integração com o chat
O CargoPlus fornece a configuração de cores utilizada pelo **ChatPlus** e também gerencia a cor associada às mensagens do jogador.

A interface `/cor` pertence ao ChatPlus; o CargoPlus fornece os dados necessários para esse sistema.

### 🔗 Integração com LoginPlus
O CargoPlus verifica o estado de autenticação do jogador através do LoginPlus. Jogadores não autenticados não recebem as permissões do cargo.

### 🧩 API
O plugin registra uma API de serviço para que outros plugins possam consultar e utilizar o sistema de cargos e permissões.

### 💾 Persistência e segurança
- Dados armazenados em arquivo configurável.
- Salvamento assíncrono para reduzir impacto no servidor.
- Snapshot dos dados antes da gravação.
- Salvamento seguro durante o desligamento.
- Recarregamento da configuração sem precisar reiniciar o servidor.

## 🎮 Comandos

| Comando | Função |
|---|---|
| `/promover <jogador>` | Promove o jogador para o próximo cargo da hierarquia. |
| `/setcargo <jogador> <cargo>` | Define um cargo configurado para o jogador. |
| `/removercargo <jogador>` | Remove o cargo personalizado e retorna ao cargo padrão. |
| `/cargo reload` | Recarrega o CargoPlus. |

O comando `/cargo` também serve como ponto de administração do plugin.

## 🔑 Permissões

| Permissão | Função | Padrão |
|---|---|---|
| `cargoplus.promover` | Promover jogadores | `false` |
| `cargoplus.setcargo` | Definir cargos | `false` |
| `cargoplus.removercargo` | Remover cargos | `false` |
| `cargoplus.admin` | Administrar o CargoPlus | `false` |

## 🔗 Dependências

- LoginPlus — utilizado para verificar se o jogador está autenticado.
- ChatPlus — integração com as cores das mensagens.

## 🏗️ Plataforma

- Java 26
- Spigot API 26.2
- Maven

## 🧪 Build

```bash
mvn -B clean package
```

O projeto possui workflow de build no GitHub Actions.
