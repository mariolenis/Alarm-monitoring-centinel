-- MySQL dump 10.13  Distrib 5.1.56, for slackware-linux-gnu (x86_64)
--
-- Host: localhost    Database: axar
-- ------------------------------------------------------
-- Server version	5.1.56

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `alarma`
--

DROP TABLE IF EXISTS `alarma`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `alarma` (
  `idAlarma` varchar(4) NOT NULL,
  `fechaCreacion` datetime NOT NULL,
  `idCliente` int(11) NOT NULL,
  `tipoAlarma` varchar(45) DEFAULT 'ANTIROBO',
  `fechaUltimoEvento` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `estado` varchar(45) DEFAULT 'APERTURA',
  `panel` varchar(100) NOT NULL,
  `teclado` varchar(100) NOT NULL,
  `moduloExpansor` varchar(100) DEFAULT 'NO',
  `modo` varchar(10) DEFAULT 'N',
  `lineaTransmision` varchar(45) DEFAULT '000000',
  `telefonoContacto` varchar(45) NOT NULL,
  `extension` varchar(6) DEFAULT '0000',
  `direccion` varchar(255) NOT NULL,
  `barrio` varchar(45) NOT NULL,
  `ciudad` varchar(45) NOT NULL DEFAULT 'VALLE DEL CAUCA - CALI',
  `difFacturacion` varchar(45) NOT NULL DEFAULT 'PROPIA',
  `GPS` varchar(45) NOT NULL,
  `claveConfirmacion` varchar(4) DEFAULT '',
  `claveCoaccion` varchar(4) DEFAULT '1010',
  `estadoActual` varchar(1) DEFAULT 'S',
  `descripcion` text,
  `servicio` int(11) DEFAULT '3',
  PRIMARY KEY (`idAlarma`),
  UNIQUE KEY `idAlarma_UNIQUE` (`idAlarma`),
  KEY `CLIENTE` (`idCliente`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `cliente`
--

DROP TABLE IF EXISTS `cliente`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `cliente` (
  `idCliente` int(11) NOT NULL AUTO_INCREMENT,
  `identificacion` varchar(45) COLLATE latin1_spanish_ci NOT NULL,
  `tipoIdentificacion` varchar(10) COLLATE latin1_spanish_ci NOT NULL,
  `fechaIngreso` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `nombre` varchar(255) COLLATE latin1_spanish_ci NOT NULL,
  `nombreEscrito` varchar(45) COLLATE latin1_spanish_ci NOT NULL,
  `telefono` varchar(45) COLLATE latin1_spanish_ci NOT NULL,
  `nombreContacto` varchar(255) COLLATE latin1_spanish_ci NOT NULL,
  `direccion` varchar(255) COLLATE latin1_spanish_ci NOT NULL,
  `barrio` varchar(100) COLLATE latin1_spanish_ci NOT NULL,
  `codCuenta` varchar(45) COLLATE latin1_spanish_ci NOT NULL DEFAULT '900008097',
  `ciudad` varchar(45) COLLATE latin1_spanish_ci DEFAULT 'CALI',
  `email` varchar(145) COLLATE latin1_spanish_ci NOT NULL,
  PRIMARY KEY (`idCliente`),
  UNIQUE KEY `identificacion_UNIQUE` (`identificacion`),
  UNIQUE KEY `idCliente_UNIQUE` (`idCliente`)
) ENGINE=MyISAM AUTO_INCREMENT=89 DEFAULT CHARSET=latin1 COLLATE=latin1_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `empleado`
--

DROP TABLE IF EXISTS `empleado`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `empleado` (
  `idempleado` varchar(12) NOT NULL,
  `cargo` varchar(45) NOT NULL,
  `nombre` varchar(45) NOT NULL,
  `zona` varchar(45) DEFAULT 'NO APLICA',
  `telefono` varchar(45) NOT NULL,
  `movil` varchar(45) NOT NULL,
  `gps` varchar(45) DEFAULT 'SIN REGISTRO',
  `ciudad` varchar(45) NOT NULL,
  `estado` varchar(1) NOT NULL DEFAULT 'A',
  `gtalk` varchar(100) DEFAULT 'NONE',
  `password` varchar(45) DEFAULT NULL,
  `rol` varchar(45) DEFAULT '0',
  PRIMARY KEY (`idempleado`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `equipo`
--

DROP TABLE IF EXISTS `equipo`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `equipo` (
  `idequipo` int(11) NOT NULL AUTO_INCREMENT,
  `tipo` varchar(45) NOT NULL DEFAULT 'ANTIROBO',
  `nombre` varchar(45) NOT NULL DEFAULT 'DSC 832',
  PRIMARY KEY (`idequipo`)
) ENGINE=MyISAM AUTO_INCREMENT=13 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `evento`
--

DROP TABLE IF EXISTS `evento`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `evento` (
  `idEvento` int(11) NOT NULL AUTO_INCREMENT,
  `fecha` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `trama` varchar(255) NOT NULL,
  `protocolo` varchar(45) DEFAULT 'CID',
  `idAlarma` varchar(4) NOT NULL,
  `particion` varchar(20) NOT NULL,
  `evento` varchar(255) DEFAULT 'SIN PROCESAR',
  `tipoEvento` varchar(45) NOT NULL,
  `categoriaEvento` varchar(45) DEFAULT 'SIN CLASIFICAR',
  `zona` varchar(3) NOT NULL,
  `tipoZona` varchar(45) DEFAULT 'DESCONOCIDA',
  `estado` varchar(45) DEFAULT 'PENDIENTE',
  `clid` varchar(255) DEFAULT '000-0000',
  `idOperacion` varchar(36) DEFAULT NULL,
  `inbound` varchar(16) DEFAULT 'SISTEMA',
  `push` varchar(1) DEFAULT 'P',
  PRIMARY KEY (`idEvento`),
  KEY `ALARMA` (`idAlarma`),
  KEY `FECHA` (`fecha`),
  KEY `ESTADO` (`estado`)
) ENGINE=MyISAM AUTO_INCREMENT=4608616 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `eventoGPS`
--

DROP TABLE IF EXISTS `eventoGPS`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `eventoGPS` (
  `idEvento` int(11) NOT NULL AUTO_INCREMENT,
  `idGPS` varchar(45) NOT NULL,
  `fecha` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `lat` varchar(45) NOT NULL,
  `lon` varchar(45) NOT NULL,
  `orientacion` varchar(45) NOT NULL,
  `tipo` varchar(45) NOT NULL,
  `bateria` varchar(45) NOT NULL,
  `estado` varchar(45) DEFAULT 'PENDIENTE',
  PRIMARY KEY (`idEvento`)
) ENGINE=MyISAM AUTO_INCREMENT=354415 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `festivos`
--

DROP TABLE IF EXISTS `festivos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `festivos` (
  `fecha` varchar(20) NOT NULL,
  `Descripcion` varchar(45) NOT NULL,
  PRIMARY KEY (`fecha`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `geocerca`
--

DROP TABLE IF EXISTS `geocerca`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `geocerca` (
  `idAlarma` varchar(5) NOT NULL,
  `geocerca` varchar(2048) NOT NULL DEFAULT '3.360231827430236 -76.54827117919922,3.349607062254695 -76.55170440673828,3.337953961416485 -76.54655456542969,3.3239015089488384 -76.54518127441406,3.31499009385735 -76.54380798339844,3.3084778551234795 -76.53865814208984,3.296824268544954 -76.5256118774414,3.291682936913672 -76.5146255493164,3.3211595436382044 -76.51359558105469,3.3403531405521765 -76.52183532714844,3.365372801331187 -76.52320861816406,3.3629736835505066 -76.51565551757812,3.38765004092332 -76.51222229003906,3.396903512996586 -76.50089263916016,3.4109549119967633 -76.48269653320312,3.3982743902097345 -76.47789001464844,3.3993025468414486 -76.4645004272461,3.423292556436189 -76.4596939086914,3.443854943734541 -76.4645004272461,3.4531078732956844 -76.47274017333984,3.49628701516024 -76.48406982421875,3.5058821108957763 -76.49505615234375,3.4942309104349047 -76.50672912597656,3.4976577491394747 -76.52252197265625,3.485321071262843 -76.53144836425781,3.472298846888741 -76.53144836425781,3.4565348613752973 -76.54518127441406,3.455164067628453 -76.56612396240234,3.449680872847543 -76.57367706298828,3.445225753794132 -76.55582427978516,3.4318602716092466 -76.55548095703125,3.422778491086786 -76.56543731689453,3.4154101907849075 -76.5692138671875,3.411468983680496 -76.56234741210938,3.4100981252463094 -76.55736923217773,3.3962180736598646 -76.55771255493164,3.3843941685865873 -76.56251907348633,3.3713705699292458 -76.55479431152344,3.360231827430236 -76.54827117919922',
  PRIMARY KEY (`idAlarma`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `googletalk`
--

DROP TABLE IF EXISTS `googletalk`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `googletalk` (
  `gtalkid` int(11) NOT NULL AUTO_INCREMENT,
  `fecha` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `mensaje` text,
  `estado` varchar(45) DEFAULT 'ENVIANDO',
  `destino` varchar(45) DEFAULT NULL,
  `idAlarma` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`gtalkid`)
) ENGINE=MyISAM AUTO_INCREMENT=34 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gps`
--

DROP TABLE IF EXISTS `gps`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gps` (
  `imei` varchar(45) NOT NULL,
  `zona` varchar(45) DEFAULT NULL,
  `estado` varchar(1) DEFAULT NULL,
  `geocerca` varchar(1024) DEFAULT NULL,
  PRIMARY KEY (`imei`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `horario`
--

DROP TABLE IF EXISTS `horario`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `horario` (
  `idhorario` int(11) NOT NULL AUTO_INCREMENT,
  `idAlarma` varchar(4) NOT NULL,
  `dia` varchar(45) NOT NULL,
  `AM` time NOT NULL,
  `PM` time NOT NULL,
  `defaultAM` time NOT NULL,
  `defaultPM` time NOT NULL,
  `last` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`idhorario`)
) ENGINE=MyISAM AUTO_INCREMENT=94 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `minuta`
--

DROP TABLE IF EXISTS `minuta`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `minuta` (
  `idMinuta` int(11) NOT NULL AUTO_INCREMENT,
  `fecha` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `idReaccion` varchar(32) COLLATE latin1_spanish_ci NOT NULL,
  `nota` text COLLATE latin1_spanish_ci NOT NULL,
  `tipoMinuta` varchar(45) COLLATE latin1_spanish_ci NOT NULL,
  PRIMARY KEY (`idMinuta`),
  KEY `REACCION` (`idReaccion`)
) ENGINE=MyISAM AUTO_INCREMENT=16410936 DEFAULT CHARSET=latin1 COLLATE=latin1_spanish_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `proveedor`
--

DROP TABLE IF EXISTS `proveedor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `proveedor` (
  `idproveedor` int(11) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(45) NOT NULL,
  `tech` varchar(45) NOT NULL,
  `proxy` varchar(45) NOT NULL,
  `prefijo` varchar(55) DEFAULT '',
  `auth` varchar(200) DEFAULT '',
  `estado` varchar(1) DEFAULT 'I',
  `okParam` varchar(10) NOT NULL,
  PRIMARY KEY (`idproveedor`)
) ENGINE=MyISAM AUTO_INCREMENT=7 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reaccion`
--

DROP TABLE IF EXISTS `reaccion`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `reaccion` (
  `idReaccion` int(11) NOT NULL AUTO_INCREMENT,
  `idAlarma` varchar(4) NOT NULL,
  `fecha` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `estado` varchar(45) DEFAULT 'PENDIENTE',
  `idSupervisor` varchar(45) DEFAULT '-1',
  `tipoReaccion` varchar(45) NOT NULL,
  `sector` varchar(10) DEFAULT '000',
  `eventos` varchar(255) NOT NULL,
  `idOperacion` varchar(36) DEFAULT NULL,
  `ultimaActualizacion` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `mensaje` text,
  PRIMARY KEY (`idReaccion`),
  KEY `ALARMA` (`idAlarma`),
  KEY `EVENTO` (`eventos`)
) ENGINE=MyISAM AUTO_INCREMENT=19525 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `servicio`
--

DROP TABLE IF EXISTS `servicio`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `servicio` (
  `idservicio` int(11) NOT NULL AUTO_INCREMENT,
  `idAlarma` varchar(45) NOT NULL,
  `tipo` varchar(45) NOT NULL DEFAULT 'REVISIÓN',
  `ordenServicio` varchar(10) DEFAULT 'NO APLICA',
  `fecha` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `motivo` text NOT NULL,
  `solicitante` varchar(45) NOT NULL,
  `estado` varchar(45) DEFAULT 'PENDIENTE',
  `encargado` varchar(45) DEFAULT 'SIN ASIGNAR',
  `apoyo` varchar(45) DEFAULT '',
  `fechaProgramacion` timestamp NULL DEFAULT NULL,
  `fechaVisita` timestamp NULL DEFAULT NULL,
  `notas` text,
  `factura` int(11) DEFAULT '0',
  `fuente` varchar(45) DEFAULT 'SIN ASIGNAR',
  PRIMARY KEY (`idservicio`)
) ENGINE=MyISAM AUTO_INCREMENT=67376 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `supervisor`
--

DROP TABLE IF EXISTS `supervisor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `supervisor` (
  `idSupervisor` int(11) NOT NULL AUTO_INCREMENT,
  `idGPS` varchar(45) NOT NULL,
  `nombre` varchar(45) NOT NULL,
  `zona` varchar(45) NOT NULL,
  `GPS` varchar(255) NOT NULL,
  `tel_1` varchar(45) NOT NULL,
  `tel_2` varchar(45) DEFAULT '0',
  `gTalk` varchar(200) DEFAULT NULL,
  `status` varchar(1) DEFAULT 'I',
  `tipo` int(11) NOT NULL DEFAULT '0',
  `turno` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`idSupervisor`)
) ENGINE=MyISAM AUTO_INCREMENT=5 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tablaCID`
--

DROP TABLE IF EXISTS `tablaCID`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tablaCID` (
  `idCID` varchar(4) NOT NULL,
  `categoria` varchar(255) DEFAULT NULL,
  `evento` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`idCID`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tablaGPS`
--

DROP TABLE IF EXISTS `tablaGPS`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tablaGPS` (
  `idGPS` varchar(4) NOT NULL,
  `categoria` varchar(45) NOT NULL,
  `evento` varchar(45) NOT NULL,
  PRIMARY KEY (`idGPS`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ubicacion`
--

DROP TABLE IF EXISTS `ubicacion`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ubicacion` (
  `idubicacion` int(11) NOT NULL AUTO_INCREMENT,
  `depto` varchar(65) DEFAULT NULL,
  `ciudad` varchar(65) DEFAULT NULL,
  `geocerca` varchar(2048) DEFAULT NULL,
  PRIMARY KEY (`idubicacion`)
) ENGINE=MyISAM AUTO_INCREMENT=1498 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `usuario`
--

DROP TABLE IF EXISTS `usuario`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `usuario` (
  `idUsuario` int(11) NOT NULL AUTO_INCREMENT,
  `idAlarma` varchar(4) NOT NULL,
  `usuario` varchar(3) NOT NULL,
  `cedula` varchar(45) NOT NULL DEFAULT '0.000.000',
  `titulo` varchar(45) DEFAULT 'Sr',
  `nombre` varchar(255) NOT NULL,
  `email` varchar(200) NOT NULL,
  `tel_1` varchar(45) NOT NULL,
  `tel_2` varchar(45) NOT NULL,
  `gtalk` varchar(45) DEFAULT 'none',
  `pass` varchar(100) DEFAULT NULL,
  `permiso` varchar(10) NOT NULL DEFAULT '0',
  PRIMARY KEY (`idUsuario`),
  KEY `ALARMA` (`idAlarma`)
) ENGINE=MyISAM AUTO_INCREMENT=344 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `vitacora`
--

DROP TABLE IF EXISTS `vitacora`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `vitacora` (
  `idvitacora` int(11) NOT NULL AUTO_INCREMENT,
  `idAlarma` varchar(4) NOT NULL,
  `fecha` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `fechaActualizacion` timestamp NULL DEFAULT NULL,
  `medio` varchar(45) NOT NULL,
  `estado` varchar(45) DEFAULT 'INFORMANDO',
  `destinatario` varchar(255) NOT NULL,
  `mensaje` text NOT NULL,
  `GUID` varchar(36) DEFAULT '-1',
  `uniqueid` varchar(45) DEFAULT 'SIN REGISTRO',
  `idOperacion` varchar(36) DEFAULT NULL,
  PRIMARY KEY (`idvitacora`)
) ENGINE=MyISAM AUTO_INCREMENT=820167 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `zona`
--

DROP TABLE IF EXISTS `zona`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `zona` (
  `idZona` int(11) NOT NULL AUTO_INCREMENT,
  `idAlarma` varchar(4) NOT NULL,
  `polaridad` varchar(1) DEFAULT '1',
  `zona` varchar(3) NOT NULL,
  `tipoZona` varchar(45) NOT NULL DEFAULT 'INTERNO',
  `descripcion` varchar(200) NOT NULL,
  PRIMARY KEY (`idZona`),
  KEY `zona` (`zona`)
) ENGINE=MyISAM AUTO_INCREMENT=443 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2016-07-05 21:07:54
